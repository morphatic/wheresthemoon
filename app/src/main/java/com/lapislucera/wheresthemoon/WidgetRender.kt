package com.lapislucera.wheresthemoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * Text-to-bitmap rendering shared by the home-screen widget and the lock
 * screen complication. RemoteViews can't use font assets directly, so
 * every piece of text is drawn to a bitmap with the app's fonts.
 */
internal object WidgetRender {

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

    /** A run of text drawn in one typeface, part of a rendered line. */
    data class Segment(val typeface: Typeface, val text: String)

    class Fonts(context: Context) {
        val kairon: Typeface = Typeface.createFromAsset(context.assets, "fonts/KaironSemiserif.ttf")
        val alegreya: Typeface = Typeface.createFromAsset(context.assets, "fonts/AlegreyaSansSC-Regular.ttf")
        val alegreyaBold: Typeface = Typeface.createFromAsset(context.assets, "fonts/AlegreyaSansSC-Bold.ttf")
    }

    fun formatTime(unixMillis: Long): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(unixMillis).atZone(ZoneId.systemDefault()))

    fun dpToPx(context: Context, dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)

    private fun paintFor(typeface: Typeface, fontSizePx: Float) = Paint().apply {
        this.typeface = typeface
        textSize = fontSizePx
        isAntiAlias = true
        isSubpixelText = true
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    /** Total width in px of a line's segments at the given font size. */
    fun lineWidth(segments: List<Segment>, fontSizePx: Float): Float =
        segments.sumOf { paintFor(it.typeface, fontSizePx).measureText(it.text).toDouble() }.toFloat()

    /**
     * Draw the moon phase as an alpha silhouette: lit portion opaque,
     * shadowed portion faint, background transparent.
     *
     * Samsung's lock screen widget host flattens widget imagery to
     * monochrome white keeping only alpha (like AOD icons), which turns
     * the photographic moon images into a featureless white disc. This
     * silhouette carries the phase entirely in the alpha channel, so it
     * survives that treatment — and it looks correct anywhere else too.
     *
     * Geometry: at elongation e the terminator is a half-ellipse with
     * signed semi-axis R·cos(e); the moon is lit on the right (waxing)
     * for e < 180° and on the left (waning) after.
     */
    fun phaseSilhouetteBitmap(context: Context, elongationDeg: Double, sizeDp: Float): Bitmap {
        val size = max(2, dpToPx(context, sizeDp).toInt())
        val n = size * 3 // supersample, then filter down for smooth edges
        val r = n / 2f
        val litRight = ((elongationDeg % 360.0) + 360.0) % 360.0 < 180.0
        val a = (r * kotlin.math.cos(Math.toRadians(elongationDeg))).toFloat()
        val lit = -0x1 // opaque white
        val shadow = 0x46FFFFFF // faint white, alpha 0x46

        val pixels = IntArray(n * n)
        for (y in 0 until n) {
            val dy = (y + 0.5f - r) / r
            val chordSq = 1f - dy * dy
            if (chordSq <= 0f) continue
            val chord = kotlin.math.sqrt(chordSq)
            val w = r * chord
            val terminator = if (litRight) a * chord else -a * chord
            for (x in 0 until n) {
                val dx = x + 0.5f - r
                if (dx < -w || dx > w) continue
                val isLit = if (litRight) dx >= terminator else dx <= terminator
                pixels[y * n + x] = if (isLit) lit else shadow
            }
        }
        val big = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(big, size, size, true)
    }

    /**
     * Render a line of mixed-typeface text to one bitmap, so the widget
     * host can never scale its parts independently.
     */
    fun lineBitmap(context: Context, segments: List<Segment>, fontSizeDp: Float): Bitmap {
        val fontSize = dpToPx(context, fontSizeDp)
        val width = max(1, lineWidth(segments, fontSize).toInt() + 1)
        val height = max(1, fontSize.toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var x = 0f
        val baseline = fontSize * 0.75f
        for (segment in segments) {
            val paint = paintFor(segment.typeface, fontSize)
            canvas.drawText(segment.text, x, baseline, paint)
            x += paint.measureText(segment.text)
        }
        return bitmap
    }
}
