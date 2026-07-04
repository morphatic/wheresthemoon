package com.lapislucera.wheresthemoon

import kotlin.math.sin

/**
 * Pure-Kotlin geocentric ephemeris for the Sun and Moon.
 *
 * Replaces the JNI Swiss Ephemeris wrapper (libwitm.so / libswe.so) the
 * original 2015 app used. Implements the algorithms from Jean Meeus,
 * "Astronomical Algorithms" 2nd ed.: chapter 25 (solar coordinates) and
 * chapter 47 (ELP-2000/82 lunar theory, full longitude term table),
 * yielding apparent longitudes of date — the same convention as the
 * swephrs engine that generates voc.db. Verified against swephrs across
 * 2015-2077 (see EphemerisTest): moon within ~20 arcsec (it drives the
 * degree display), sun within ~40 arcsec (it only selects the phase
 * image, which is bucketed at 18 degrees).
 */
object Ephemeris {

    private const val DEG = Math.PI / 180.0

    /** Julian Day (UT) for a unix timestamp in milliseconds. */
    fun julianDay(unixMillis: Long): Double = unixMillis / 86400000.0 + 2440587.5

    /**
     * ΔT = TT − UT in seconds.
     *
     * Quadratic fitted (2026-07-04) to the swephrs implementation of the
     * Swiss Ephemeris Stephenson-2016/AA-table ΔT model at seven epochs
     * spanning 2015-2077; max residual 0.31 s over that range, i.e. under
     * 0.2 arcseconds of lunar motion. Matching swephrs matters more than
     * matching any observatory projection, because the bundled voc.db is
     * generated with swephrs (tools/vocgen) and the live display must
     * agree with it at the ingress instants.
     */
    fun deltaT(jdUt: Double): Double {
        val y = (jdUt - 2451545.0) / 365.25 // years since 2000.0
        return 66.44725 + 0.03806258 * y + 0.0024191882 * y * y
    }

    /** Apparent geocentric ecliptic longitude of the Sun, degrees [0, 360). */
    fun sunLongitude(jdUt: Double): Double {
        val t = (jdUt + deltaT(jdUt) / 86400.0 - 2451545.0) / 36525.0

        // Mean longitude and mean anomaly (Meeus 25.2, 25.3).
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) * DEG

        // Equation of center.
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)

        // Apparent longitude: nutation plus aberration (−20.5″), matching
        // the swephrs/swe_calc_ut default convention. Residual error is
        // Meeus ch. 25 truncation, bounded at ~0.01°.
        return norm360(l0 + c + nutationLongitude(t) - 0.00569)
    }

    /** Apparent geocentric ecliptic longitude of the Moon, degrees [0, 360). */
    fun moonLongitude(jdUt: Double): Double {
        val t = (jdUt + deltaT(jdUt) / 86400.0 - 2451545.0) / 36525.0
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        // Fundamental arguments (Meeus 47.1-47.6), degrees.
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
        val mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t

        // Eccentricity correction factor for terms involving M.
        val e = 1.0 - 0.002516 * t - 0.0000074 * t2
        val e2 = e * e

        // Σl: periodic longitude terms (Meeus table 47.A), unit 1e-6 deg.
        var sigmaL = 0.0
        for (term in LONGITUDE_TERMS) {
            val arg = (term[0] * d + term[1] * m + term[2] * mp + term[3] * f) * DEG
            var coeff = term[4]
            when (term[1] * term[1]) {
                1.0 -> coeff *= e
                4.0 -> coeff *= e2
            }
            sigmaL += coeff * sin(arg)
        }
        // Additive terms: Venus (A1), Jupiter (A2), and flattening (L'−F).
        sigmaL += 3958.0 * sin(a1 * DEG) + 1962.0 * sin((lp - f) * DEG) + 318.0 * sin(a2 * DEG)

        // Geometric longitude + nutation → apparent longitude.
        return norm360(lp + sigmaL / 1_000_000.0 + nutationLongitude(t))
    }

    /** Nutation in longitude Δψ in degrees (principal terms, IAU 1980). */
    private fun nutationLongitude(t: Double): Double {
        val omega = (125.04452 - 1934.136261 * t) * DEG // lunar ascending node
        val ls = (280.4665 + 36000.7698 * t) * DEG // mean sun
        val lm = (218.3165 + 481267.8813 * t) * DEG // mean moon
        return (-17.20 * sin(omega) - 1.32 * sin(2 * ls) - 0.23 * sin(2 * lm) +
            0.21 * sin(2 * omega)) / 3600.0
    }

    private fun norm360(deg: Double): Double {
        val x = deg % 360.0
        return if (x < 0) x + 360.0 else x
    }

    // Meeus table 47.A: multipliers of D, M, M', F and the sine coefficient.
    @Suppress("ktlint")
    private val LONGITUDE_TERMS = arrayOf(
        doubleArrayOf(0.0, 0.0, 1.0, 0.0, 6288774.0),
        doubleArrayOf(2.0, 0.0, -1.0, 0.0, 1274027.0),
        doubleArrayOf(2.0, 0.0, 0.0, 0.0, 658314.0),
        doubleArrayOf(0.0, 0.0, 2.0, 0.0, 213618.0),
        doubleArrayOf(0.0, 1.0, 0.0, 0.0, -185116.0),
        doubleArrayOf(0.0, 0.0, 0.0, 2.0, -114332.0),
        doubleArrayOf(2.0, 0.0, -2.0, 0.0, 58793.0),
        doubleArrayOf(2.0, -1.0, -1.0, 0.0, 57066.0),
        doubleArrayOf(2.0, 0.0, 1.0, 0.0, 53322.0),
        doubleArrayOf(2.0, -1.0, 0.0, 0.0, 45758.0),
        doubleArrayOf(0.0, 1.0, -1.0, 0.0, -40923.0),
        doubleArrayOf(1.0, 0.0, 0.0, 0.0, -34720.0),
        doubleArrayOf(0.0, 1.0, 1.0, 0.0, -30383.0),
        doubleArrayOf(2.0, 0.0, 0.0, -2.0, 15327.0),
        doubleArrayOf(0.0, 0.0, 1.0, 2.0, -12528.0),
        doubleArrayOf(0.0, 0.0, 1.0, -2.0, 10980.0),
        doubleArrayOf(4.0, 0.0, -1.0, 0.0, 10675.0),
        doubleArrayOf(0.0, 0.0, 3.0, 0.0, 10034.0),
        doubleArrayOf(4.0, 0.0, -2.0, 0.0, 8548.0),
        doubleArrayOf(2.0, 1.0, -1.0, 0.0, -7888.0),
        doubleArrayOf(2.0, 1.0, 0.0, 0.0, -6766.0),
        doubleArrayOf(1.0, 0.0, -1.0, 0.0, -5163.0),
        doubleArrayOf(1.0, 1.0, 0.0, 0.0, 4987.0),
        doubleArrayOf(2.0, -1.0, 1.0, 0.0, 4036.0),
        doubleArrayOf(2.0, 0.0, 2.0, 0.0, 3994.0),
        doubleArrayOf(4.0, 0.0, 0.0, 0.0, 3861.0),
        doubleArrayOf(2.0, 0.0, -3.0, 0.0, 3665.0),
        doubleArrayOf(0.0, 1.0, -2.0, 0.0, -2689.0),
        doubleArrayOf(2.0, 0.0, -1.0, 2.0, -2602.0),
        doubleArrayOf(2.0, -1.0, -2.0, 0.0, 2390.0),
        doubleArrayOf(1.0, 0.0, 1.0, 0.0, -2348.0),
        doubleArrayOf(2.0, -2.0, 0.0, 0.0, 2236.0),
        doubleArrayOf(0.0, 1.0, 2.0, 0.0, -2120.0),
        doubleArrayOf(0.0, 2.0, 0.0, 0.0, -2069.0),
        doubleArrayOf(2.0, -2.0, -1.0, 0.0, 2048.0),
        doubleArrayOf(2.0, 0.0, 1.0, -2.0, -1773.0),
        doubleArrayOf(2.0, 0.0, 0.0, 2.0, -1595.0),
        doubleArrayOf(4.0, -1.0, -1.0, 0.0, 1215.0),
        doubleArrayOf(0.0, 0.0, 2.0, 2.0, -1110.0),
        doubleArrayOf(3.0, 0.0, -1.0, 0.0, -892.0),
        doubleArrayOf(2.0, 1.0, 1.0, 0.0, -810.0),
        doubleArrayOf(4.0, -1.0, -2.0, 0.0, 759.0),
        doubleArrayOf(0.0, 2.0, -1.0, 0.0, -713.0),
        doubleArrayOf(2.0, 2.0, -1.0, 0.0, -700.0),
        doubleArrayOf(2.0, 1.0, -2.0, 0.0, 691.0),
        doubleArrayOf(2.0, -1.0, 0.0, -2.0, 596.0),
        doubleArrayOf(4.0, 0.0, 1.0, 0.0, 549.0),
        doubleArrayOf(0.0, 0.0, 4.0, 0.0, 537.0),
        doubleArrayOf(4.0, -1.0, 0.0, 0.0, 520.0),
        doubleArrayOf(1.0, 0.0, -2.0, 0.0, -487.0),
        doubleArrayOf(2.0, 1.0, 0.0, -2.0, -399.0),
        doubleArrayOf(0.0, 0.0, 2.0, -2.0, -381.0),
        doubleArrayOf(1.0, 1.0, 1.0, 0.0, 351.0),
        doubleArrayOf(3.0, 0.0, -2.0, 0.0, -340.0),
        doubleArrayOf(4.0, 0.0, -3.0, 0.0, 330.0),
        doubleArrayOf(2.0, -1.0, 2.0, 0.0, 327.0),
        doubleArrayOf(0.0, 2.0, 1.0, 0.0, -323.0),
        doubleArrayOf(1.0, 1.0, -1.0, 0.0, 299.0),
        doubleArrayOf(2.0, 0.0, 3.0, 0.0, 294.0),
    )
}
