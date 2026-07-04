//! vocgen — regenerates the `voc.db` SQLite database used by the
//! Where's the Moon Android app.
//!
//! For every moon sign ingress in the requested range, records the exact
//! time of the *last* Ptolemaic aspect (conjunction, sextile, square,
//! trine, opposition) the Moon makes to Sun/Mercury..Pluto before that
//! ingress — i.e. the start of the void-of-course period.
//!
//! Schema (identical to the original 2015 app):
//!   CREATE TABLE voc ( sign int, ingress int primary key,
//!                      aspect int, planet int, asptime int );
//!   sign    0..11  sign the Moon enters AT `ingress` (0 = Aries)
//!   ingress        unix time (UTC) of the sign ingress
//!   aspect  0..4   0=conjunction 1=sextile 2=square 3=trine 4=opposition
//!   planet  0..9   0=Sun 1=Moon(unused) 2=Mercury 3=Venus 4=Mars
//!                  5=Jupiter 6=Saturn 7=Uranus 8=Neptune 9=Pluto
//!   asptime        unix time (UTC) of the last exact aspect before ingress
//!
//! PRAGMA user_version is set to the generation date (YYYYMMDD); the app
//! uses it to decide when to replace the device copy of the database.
//!
//! Usage: vocgen <start YYYY-MM-DD> <end YYYY-MM-DD> <out.db> [user_version]

use rusqlite::Connection;
use swephrs::prelude::*;

/// Bodies the Moon is checked against, with their index in the app's
/// `planets` glyph array (index 1 = Moon itself, deliberately absent).
const BODIES: [(CelestialBody, i64); 9] = [
    (CelestialBody::Sun, 0),
    (CelestialBody::Mercury, 2),
    (CelestialBody::Venus, 3),
    (CelestialBody::Mars, 4),
    (CelestialBody::Jupiter, 5),
    (CelestialBody::Saturn, 6),
    (CelestialBody::Uranus, 7),
    (CelestialBody::Neptune, 8),
    (CelestialBody::Pluto, 9),
];

/// Separation angles at which each aspect is exact, with the app's
/// aspect index. Sextile/square/trine occur on both sides of conjunction.
const TARGETS: [(f64, i64); 8] = [
    (0.0, 0),
    (60.0, 1),
    (90.0, 2),
    (120.0, 3),
    (180.0, 4),
    (240.0, 3),
    (270.0, 2),
    (300.0, 1),
];

const UNIX_EPOCH_JD: f64 = 2440587.5;
/// Scan step. Max Moon-vs-planet relative motion is ~0.7 deg/hour, so a
/// one-hour grid moves less than a degree per step and cannot skip a
/// crossing given the |f| < 10 deg filter in `scan`.
const STEP: f64 = 3600.0;

fn unix_to_jd(t: f64) -> f64 {
    t / 86400.0 + UNIX_EPOCH_JD
}

/// Ecliptic longitude of `body` at unix time `t` (geocentric, tropical).
fn lon_at(ctx: &EphemerisContext, body: CelestialBody, t: f64) -> f64 {
    calc_body_ut(
        ctx,
        body,
        JulianDay::from(unix_to_jd(t)),
        CalculationFlags::empty(),
    )
    .into_value()
    .longitude
}

/// Wrap an angle to (-180, 180].
fn wrap180(a: f64) -> f64 {
    let mut a = a % 360.0;
    if a <= -180.0 {
        a += 360.0;
    } else if a > 180.0 {
        a -= 360.0;
    }
    a
}

/// Find the zero of a monotonic-enough angular function `f` (degrees,
/// wrapped) between t0 and t1 where f(t0) and f(t1) straddle zero.
/// Bisects to better than half a second.
fn bisect(f: impl Fn(f64) -> f64, mut t0: f64, mut t1: f64) -> f64 {
    let mut f0 = f(t0);
    while t1 - t0 > 0.5 {
        let tm = (t0 + t1) / 2.0;
        let fm = f(tm);
        if (f0 <= 0.0) == (fm <= 0.0) {
            t0 = tm;
            f0 = fm;
        } else {
            t1 = tm;
        }
    }
    (t0 + t1) / 2.0
}

/// Parse "YYYY-MM-DD" into a unix timestamp at 00:00:00 UTC.
/// Days-from-civil algorithm (Howard Hinnant), valid for all Gregorian dates.
fn parse_date(s: &str) -> f64 {
    let parts: Vec<i64> = s.split('-').map(|p| p.parse().expect("bad date")).collect();
    assert!(parts.len() == 3, "expected YYYY-MM-DD, got {s}");
    let (mut y, m, d) = (parts[0], parts[1], parts[2]);
    y -= if m <= 2 { 1 } else { 0 };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400;
    let doy = (153 * (m + if m > 2 { -3 } else { 9 }) + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    ((era * 146097 + doe - 719468) * 86400) as f64
}

struct Crossing {
    t: f64,
    aspect: i64,
    planet: i64,
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 4 {
        eprintln!("usage: vocgen <start YYYY-MM-DD> <end YYYY-MM-DD> <out.db> [user_version]");
        std::process::exit(2);
    }
    let start = parse_date(&args[1]);
    let end = parse_date(&args[2]);
    let out = &args[3];
    let user_version: i64 = args.get(4).map(|v| v.parse().unwrap()).unwrap_or(0);
    assert!(end > start, "end must be after start");

    let ctx = EphemerisContext::builder().build().expect("ephemeris context");

    // Begin the scan several days early so the first ingress in range has
    // aspect history behind it.
    let scan_start = start - 5.0 * 86400.0;

    let mut ingresses: Vec<(f64, i64)> = Vec::new(); // (unix, sign entered)
    let mut crossings: Vec<Crossing> = Vec::new(); // chronological

    let moon = |t: f64| lon_at(&ctx, CelestialBody::Moon, t);

    let mut prev_t = scan_start;
    let mut prev_moon = moon(prev_t);
    let mut prev_seps: Vec<f64> = BODIES
        .iter()
        .map(|&(b, _)| {
            let mut s = prev_moon - lon_at(&ctx, b, prev_t);
            if s < 0.0 {
                s += 360.0;
            }
            s
        })
        .collect();

    let mut t = scan_start + STEP;
    while t <= end + STEP {
        let moon_lon = moon(t);

        // -- sign ingress: Moon's longitude crossed a multiple of 30 deg --
        if (prev_moon / 30.0).floor() != (moon_lon / 30.0).floor() || moon_lon < prev_moon {
            let boundary = (moon_lon / 30.0).floor() * 30.0;
            let ti = bisect(|x| wrap180(moon(x) - boundary), prev_t, t);
            ingresses.push((ti, (boundary / 30.0).round() as i64 % 12));
        }

        // -- aspect crossings against each body --
        for (i, &(body, planet_idx)) in BODIES.iter().enumerate() {
            let mut sep = moon_lon - lon_at(&ctx, body, t);
            if sep < 0.0 {
                sep += 360.0;
            }
            for &(angle, aspect_idx) in &TARGETS {
                let f0 = wrap180(prev_seps[i] - angle);
                let f1 = wrap180(sep - angle);
                // A genuine crossing has small |f| on both sides (the
                // separation changes < 1 deg per step); this also rejects
                // the spurious sign flip at the +/-180 wrap point.
                if (f0 <= 0.0) != (f1 <= 0.0) && f0.abs() < 10.0 && f1.abs() < 10.0 {
                    let tc = bisect(
                        |x| {
                            let mut s = moon(x) - lon_at(&ctx, body, x);
                            if s < 0.0 {
                                s += 360.0;
                            }
                            wrap180(s - angle)
                        },
                        prev_t,
                        t,
                    );
                    crossings.push(Crossing {
                        t: tc,
                        aspect: aspect_idx,
                        planet: planet_idx,
                    });
                }
            }
            prev_seps[i] = sep;
        }

        prev_moon = moon_lon;
        prev_t = t;
        t += STEP;
    }

    crossings.sort_by(|a, b| a.t.partial_cmp(&b.t).unwrap());

    // -- write the database --
    let _ = std::fs::remove_file(out);
    let db = Connection::open(out).expect("create db");
    db.execute_batch(&format!(
        "CREATE TABLE voc ( sign int, ingress int primary key, aspect int, planet int, asptime int );
         PRAGMA user_version = {user_version};"
    ))
    .unwrap();

    let mut rows = 0u32;
    let mut ci = 0usize; // walking pointer into crossings
    for &(ti, sign) in &ingresses {
        if ti < start || ti > end {
            continue;
        }
        while ci + 1 < crossings.len() && crossings[ci + 1].t < ti {
            ci += 1;
        }
        let c = &crossings[ci];
        assert!(c.t < ti, "no aspect found before ingress at {ti}");
        db.execute(
            "INSERT INTO voc (sign, ingress, aspect, planet, asptime) VALUES (?1, ?2, ?3, ?4, ?5)",
            (
                sign,
                ti.round() as i64,
                c.aspect,
                c.planet,
                c.t.round() as i64,
            ),
        )
        .unwrap();
        rows += 1;
    }

    println!("{rows} rows written to {out} (user_version {user_version})");
}
