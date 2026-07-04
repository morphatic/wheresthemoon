package com.lapislucera.wheresthemoon

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure display logic for the widget and daydream: glyph lookup tables for
 * the Kairon Semiserif astrological font, moon-phase image selection, and
 * degree formatting. Kept free of Android classes so it is unit-testable.
 */
object MoonDisplay {

    /** Zodiac sign glyphs, Aries..Pisces, in the Kairon Semiserif font. */
    val SIGNS = arrayOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p", "ü", "+")

    /** Planet glyphs, Sun..Pluto (index 1 = Moon, unused in VOC data). */
    val PLANETS = arrayOf("a", "s", "d", "f", "h", "j", "k", "ö", "ä", "#")

    /** Aspect glyphs: conjunction, sextile, square, trine, opposition. */
    val ASPECTS = arrayOf("<", "x", "c", "Q", "m")

    /** Zodiac sign index (0 = Aries) for an ecliptic longitude. */
    fun signIndex(longitude: Double): Int = (floor(longitude / 30.0).toInt() % 12 + 12) % 12

    /**
     * Drawable name for the moon-phase image nearest the Sun-Moon
     * elongation. Images exist for phase centers 009, 027, ... 351
     * (20 images, 18° apart; 009 ≈ new moon, 189 ≈ full moon).
     */
    fun phaseDrawableName(moonLongitude: Double, sunLongitude: Double): String {
        var elongation = (moonLongitude - sunLongitude) % 360.0
        if (elongation < 0) elongation += 360.0
        // Bucket the elongation; the % 360 guards the top edge, where the
        // original app rounded up to a nonexistent "moon_369".
        val bucket = (18 * floor(elongation.roundToInt() / 18.0).toInt() + 9) % 360
        return "moon_%03d".format(bucket)
    }

    /**
     * Format decimal degrees as degrees°minutes'seconds", carrying
     * rounded-up seconds so 29.99999 renders as 30°0'0", not 29°59'60".
     */
    fun dms(decimal: Double): String {
        var totalSeconds = (decimal * 3600.0).roundToInt()
        val deg = totalSeconds / 3600
        totalSeconds -= deg * 3600
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return "$deg°$min'$sec\""
    }
}
