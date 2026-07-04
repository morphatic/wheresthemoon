package com.lapislucera.wheresthemoon

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * Verifies the pure-Kotlin ephemeris against Swiss Ephemeris reference
 * positions (apparent geocentric ecliptic longitudes, obtained from the
 * Morphemeris API on 2026-07-04) across the app's data range 2015-2077.
 *
 * Tolerances: the widget displays the moon's position to the arcsecond
 * but the value drifts one arcsecond every ~2 seconds of wall time, so
 * agreement within a few arcseconds is more than sufficient.
 */
class EphemerisTest {

    private data class Ref(val instant: String, val sunLon: Double, val moonLon: Double)

    private val references = listOf(
        Ref("2015-07-01T09:10:59Z", 99.24069118440818, 269.9999127178795),
        Ref("2026-01-01T00:00:00Z", 280.568586935551, 66.71559967950587),
        Ref("2026-07-04T21:00:00Z", 102.89667705399587, 337.54197366607576),
        Ref("2030-06-15T12:00:00Z", 84.45152238703193, 260.51457637112514),
        Ref("2036-03-20T06:30:00Z", 0.22578679128899637, 275.6364382446009),
        Ref("2044-11-11T11:11:11Z", 229.70648622092548, 129.95876886319837),
        Ref("2050-01-01T00:00:00Z", 280.74843930036013, 18.676432664844356),
        Ref("2060-08-08T20:00:00Z", 137.03650365763522, 276.5655703720141),
        Ref("2070-04-30T03:45:00Z", 40.17420568084394, 272.05786749266514),
        Ref("2076-12-31T21:07:01Z", 281.0776932693191, 359.99995361808425),
    )

    // The Sun feeds only the moon-phase image, which buckets the Sun-Moon
    // elongation at 18°; Meeus ch. 25's stated accuracy of 0.01° (36″) is
    // three orders of magnitude finer than that consumer needs.
    private val sunToleranceDeg = 40.0 / 3600.0 // 40 arcseconds
    // The Moon drives the deg°min'sec" display; it uses the full ch. 47
    // term table and must track the database's engine closely.
    private val moonToleranceDeg = 20.0 / 3600.0 // 20 arcseconds

    private fun jd(instant: String): Double =
        Ephemeris.julianDay(Instant.parse(instant).toEpochMilli())

    private fun angleError(expected: Double, actual: Double): Double {
        var d = abs(expected - actual) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }

    @Test
    fun sunLongitudeMatchesSwissEphemeris() {
        for (ref in references) {
            val actual = Ephemeris.sunLongitude(jd(ref.instant))
            val err = angleError(ref.sunLon, actual)
            assertEquals(
                "sun @ ${ref.instant}: expected ${ref.sunLon}, got $actual (err ${err * 3600} arcsec)",
                0.0,
                err,
                sunToleranceDeg,
            )
        }
    }

    @Test
    fun moonLongitudeMatchesSwissEphemeris() {
        for (ref in references) {
            val actual = Ephemeris.moonLongitude(jd(ref.instant))
            val err = angleError(ref.moonLon, actual)
            assertEquals(
                "moon @ ${ref.instant}: expected ${ref.moonLon}, got $actual (err ${err * 3600} arcsec)",
                0.0,
                err,
                moonToleranceDeg,
            )
        }
    }

    @Test
    fun julianDayForKnownEpochs() {
        // 2000-01-01T12:00:00Z is exactly JD 2451545.0.
        assertEquals(2451545.0, Ephemeris.julianDay(946728000000L), 1e-9)
        // The unix epoch itself.
        assertEquals(2440587.5, Ephemeris.julianDay(0L), 1e-9)
    }

    @Test
    fun deltaTIsPlausibleForDataRange() {
        // ΔT is ~69s in the mid-2020s, growing slowly; sanity-bound it
        // rather than pinning exact values (Earth's rotation wobbles).
        val dt2026 = Ephemeris.deltaT(jd("2026-01-01T00:00:00Z"))
        val dt2076 = Ephemeris.deltaT(jd("2076-01-01T00:00:00Z"))
        assert(dt2026 in 60.0..90.0) { "ΔT(2026) = $dt2026" }
        assert(dt2076 in 60.0..180.0) { "ΔT(2076) = $dt2076" }
        assert(dt2076 > dt2026)
    }
}
