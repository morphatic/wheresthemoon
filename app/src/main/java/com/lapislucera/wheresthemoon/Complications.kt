package com.lapislucera.wheresthemoon

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/**
 * The two compact widgets for the Samsung One UI lock screen
 * ("complications"; see res/xml/wtm_aspect_info.xml for how they get
 * into that picker). One UI grants lock widgets ~123x54dp and fits two
 * side by side under the clock, so the two key void-of-course facts get
 * one tile each:
 *
 *  - [WTMAspectComplication]  the last aspect before the next ingress
 *  - [WTMIngressComplication] the next sign ingress
 *
 * Void-of-course status is carried by the text color of both tiles:
 * white while the moon is not void, gold while it is. Each tile
 * measures its allotted space and scales its text to fill it. They are
 * also placeable as small home-screen widgets.
 */
class WTMAspectComplication : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WTMAppWidget.scheduleAlarms(context)
        for (id in appWidgetIds) ComplicationRenderer.renderAspectTile(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        ComplicationRenderer.renderAspectTile(context, appWidgetManager, appWidgetId)
    }
}

class WTMIngressComplication : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WTMAppWidget.scheduleAlarms(context)
        for (id in appWidgetIds) ComplicationRenderer.renderIngressTile(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        ComplicationRenderer.renderIngressTile(context, appWidgetManager, appWidgetId)
    }
}

internal object ComplicationRenderer {

    /** Text color while the moon is void of course (amber gold). */
    const val VOID_COLOR = 0xFFFFC107.toInt()
    const val NOT_VOID_COLOR = Color.WHITE

    // Base line sizes (dp); the fit computation scales both lines of a
    // tile together, up or down, to fill the space Samsung grants.
    private const val LINE1_FONT_DP = 18f
    private const val LINE2_FONT_DP = 16f
    private const val MAX_SCALE = 1.8f
    private const val LINE_GAP_DP = 3f
    private const val TILE_PADDING_DP = 12f // layout padding, both axes

    // Observed One UI lock tile grant is ~123x54dp; used when the host
    // supplies no size options at all.
    private const val FALLBACK_WIDTH_DP = 123f
    private const val FALLBACK_HEIGHT_DP = 54f

    /** Refresh every placed instance of both complication tiles. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for ((cls, render) in listOf<Pair<Class<*>, (Context, AppWidgetManager, Int) -> Unit>>(
            WTMAspectComplication::class.java to ::renderAspectTile,
            WTMIngressComplication::class.java to ::renderIngressTile,
        )) {
            for (id in manager.getAppWidgetIds(ComponentName(context, cls))) {
                render(context, manager, id)
            }
        }
    }

    fun renderAspectTile(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val fonts = WidgetRender.Fonts(context)
        val voc = VocDatabase(context).getCurrentVoc()
        val line1 = if (voc == null) {
            listOf(WidgetRender.Segment(fonts.alegreyaBold, "Last Aspect"))
        } else {
            // During the void period the label itself flags the state —
            // the lock screen host strips the gold color.
            val label = if (isVoid(voc)) "Void since " else "Last Aspect "
            listOf(
                WidgetRender.Segment(fonts.alegreyaBold, label),
                WidgetRender.Segment(fonts.kairon, MoonDisplay.ASPECTS[voc.aspect] + " " + MoonDisplay.PLANETS[voc.planet]),
            )
        }
        val line2 = listOf(
            WidgetRender.Segment(
                fonts.alegreya,
                voc?.let { WidgetRender.formatTime(it.asptime / 60 * 60000) } ?: "No data",
            ),
        )
        renderTile(context, manager, appWidgetId, line1, line2, statusColor(voc))
    }

    fun renderIngressTile(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val fonts = WidgetRender.Fonts(context)
        val line1: List<WidgetRender.Segment>
        val line2: List<WidgetRender.Segment>
        val voc = VocDatabase(context).getCurrentVoc()
        if (voc == null) {
            line1 = listOf(WidgetRender.Segment(fonts.alegreyaBold, "Ingress"))
            line2 = listOf(WidgetRender.Segment(fonts.alegreya, "No data"))
        } else {
            val label = if (isVoid(voc)) "Void until " else "Ingress "
            line1 = listOf(
                WidgetRender.Segment(fonts.alegreyaBold, label),
                WidgetRender.Segment(fonts.kairon, MoonDisplay.SIGNS[voc.sign]),
            )
            line2 = listOf(WidgetRender.Segment(fonts.alegreya, WidgetRender.formatTime(voc.ingress / 60 * 60000)))
        }
        renderTile(context, manager, appWidgetId, line1, line2, statusColor(voc))
    }

    /** Void right now? Minute-rounded, matching the displayed times. */
    fun isVoid(voc: VocDatabase.VocInfo?, nowMillis: Long = System.currentTimeMillis()): Boolean =
        voc != null && nowMillis in (voc.asptime / 60 * 60000) until (voc.ingress / 60 * 60000)

    /**
     * White while not void, gold while void. The gold only survives on
     * home-screen placements: the One UI lock host strips color and
     * re-tints bitmaps (verified by pixel-sampling a lock screenshot),
     * which is why the tile labels also change during the void period.
     */
    fun statusColor(voc: VocDatabase.VocInfo?, nowMillis: Long = System.currentTimeMillis()): Int =
        if (isVoid(voc, nowMillis)) VOID_COLOR else NOT_VOID_COLOR

    private fun renderTile(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        line1: List<WidgetRender.Segment>,
        line2: List<WidgetRender.Segment>,
        color: Int,
    ) {
        val (widthDp, heightDp) = tileSize(manager, appWidgetId)
        val views = RemoteViews(context.packageName, R.layout.wtm_complication_text)

        val availWpx = WidgetRender.dpToPx(context, max(40f, widthDp - TILE_PADDING_DP))
        val availHpx = WidgetRender.dpToPx(context, max(24f, heightDp - TILE_PADDING_DP - LINE_GAP_DP))
        val w1 = WidgetRender.lineWidth(line1, WidgetRender.dpToPx(context, LINE1_FONT_DP))
        val w2 = WidgetRender.lineWidth(line2, WidgetRender.dpToPx(context, LINE2_FONT_DP))
        val heightAtBase = WidgetRender.dpToPx(context, LINE1_FONT_DP + LINE2_FONT_DP)
        val scale = min(MAX_SCALE, min(min(availWpx / w1, availWpx / w2), availHpx / heightAtBase))

        views.setImageViewBitmap(R.id.comp_line1, WidgetRender.lineBitmap(context, line1, LINE1_FONT_DP * scale, color))
        views.setImageViewBitmap(R.id.comp_line2, WidgetRender.lineBitmap(context, line2, LINE2_FONT_DP * scale, color))
        manager.updateAppWidget(appWidgetId, views)
    }

    /**
     * The tile's granted size in dp. The One UI lock host supplies only
     * OPTION_APPWIDGET_SIZES (as SizeF), not the MIN_WIDTH/HEIGHT ints
     * that launchers set, so try both before falling back.
     */
    private fun tileSize(manager: AppWidgetManager, appWidgetId: Int): Pair<Float, Float> {
        val options = manager.getAppWidgetOptions(appWidgetId)
        @Suppress("DEPRECATION")
        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        if (!sizes.isNullOrEmpty()) {
            return sizes[0].width to sizes[0].height
        }
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .takeIf { it > 0 }?.toFloat() ?: FALLBACK_WIDTH_DP
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            .takeIf { it > 0 }?.toFloat() ?: FALLBACK_HEIGHT_DP
        return w to h
    }
}
