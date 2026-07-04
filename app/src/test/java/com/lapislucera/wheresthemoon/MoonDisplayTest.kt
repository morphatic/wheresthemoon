package com.lapislucera.wheresthemoon

import org.junit.Assert.assertEquals
import org.junit.Test

class MoonDisplayTest {

    // ── phase image selection ──────────────────────────────────────────

    @Test
    fun newMoonSelectsFirstImage() {
        assertEquals("moon_009", MoonDisplay.phaseDrawableName(100.0, 100.0))
    }

    @Test
    fun fullMoonSelectsOppositionImage() {
        assertEquals("moon_189", MoonDisplay.phaseDrawableName(280.0, 100.0))
    }

    @Test
    fun waningCrescentJustBeforeNewMoon() {
        // Elongation 350° falls in the 342-359 bucket.
        assertEquals("moon_351", MoonDisplay.phaseDrawableName(90.0, 100.0))
    }

    @Test
    fun elongationRoundingUpTo360WrapsToNewMoon() {
        // Elongation 359.9° rounds to 360; the original 2015 app computed
        // a nonexistent "moon_369" here and showed a blank icon.
        assertEquals("moon_009", MoonDisplay.phaseDrawableName(99.9, 100.0))
    }

    @Test
    fun phaseNameAlwaysExistsInTheTwentyImageSet() {
        val valid = (0..19).map { "moon_%03d".format(18 * it + 9) }.toSet()
        var lon = 0.0
        while (lon < 360.0) {
            val name = MoonDisplay.phaseDrawableName(lon, 0.0)
            assert(name in valid) { "elongation $lon produced $name" }
            lon += 0.05
        }
    }

    // ── sign index ─────────────────────────────────────────────────────

    @Test
    fun signIndexCoversAllTwelveSigns() {
        assertEquals(0, MoonDisplay.signIndex(0.0)) // 0° Aries
        assertEquals(0, MoonDisplay.signIndex(29.999)) // late Aries
        assertEquals(1, MoonDisplay.signIndex(30.0)) // 0° Taurus
        assertEquals(9, MoonDisplay.signIndex(270.0)) // 0° Capricorn
        assertEquals(11, MoonDisplay.signIndex(359.999)) // late Pisces
    }

    // ── degree formatting ──────────────────────────────────────────────

    @Test
    fun dmsFormatsWholeAndFractionalDegrees() {
        assertEquals("0°0'0\"", MoonDisplay.dms(0.0))
        assertEquals("15°30'0\"", MoonDisplay.dms(15.5))
        assertEquals("7°32'42\"", MoonDisplay.dms(7.545))
    }

    @Test
    fun dmsCarriesRoundedSecondsInsteadOfShowingSixty() {
        // 29.999999° is 29°59'59.9964"; the original app rendered 29°59'60".
        assertEquals("30°0'0\"", MoonDisplay.dms(29.999999))
        assertEquals("1°0'0\"", MoonDisplay.dms(0.9999999))
    }

    // ── glyph tables ───────────────────────────────────────────────────

    @Test
    fun glyphTablesMatchDatabaseCodeRanges() {
        // voc.db encodes sign 0-11, aspect 0-4, planet 0-9.
        assertEquals(12, MoonDisplay.SIGNS.size)
        assertEquals(5, MoonDisplay.ASPECTS.size)
        assertEquals(10, MoonDisplay.PLANETS.size)
    }
}
