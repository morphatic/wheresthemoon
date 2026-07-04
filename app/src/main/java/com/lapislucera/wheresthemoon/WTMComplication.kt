package com.lapislucera.wheresthemoon

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/**
 * Compact widget for the Samsung One UI lock screen ("complication"; see
 * res/xml/wtm_complication_info.xml for how it gets into that picker).
 * Shows the moon phase image, the moon's sign and degree, and when the
 * current or next void period ends or begins. Also placeable as a small
 * 2x1 home-screen widget.
 */
class WTMComplication : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WTMAppWidget.scheduleAlarms(context)
        for (appWidgetId in appWidgetIds) {
            updateComplication(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateComplication(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private const val MOON_ICON_DP = 48f
        private const val POSITION_FONT_DP = 16f
        private const val STATUS_FONT_DP = 13f

        // Horizontal space (dp) not available to the text column: layout
        // padding (4+4), moon icon (48), text column padding (8).
        private const val NON_TEXT_DP = 64f
        private const val FALLBACK_WIDTH_DP = 160f

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WTMComplication::class.java))
            for (id in ids) {
                updateComplication(context, manager, id)
            }
        }

        fun updateComplication(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.wtm_complication)
            val fonts = WidgetRender.Fonts(context)

            val jd = Ephemeris.julianDay(System.currentTimeMillis())
            val moonLon = Ephemeris.moonLongitude(jd)
            val sunLon = Ephemeris.sunLongitude(jd)

            // An alpha silhouette rather than the photographic moon: the
            // Samsung lock screen host whitens imagery, keeping only alpha.
            val elongation = ((moonLon - sunLon) % 360.0 + 360.0) % 360.0
            views.setImageViewBitmap(
                R.id.comp_moon_icon,
                WidgetRender.phaseSilhouetteBitmap(context, elongation, MOON_ICON_DP),
            )

            val positionLine = listOf(
                WidgetRender.Segment(fonts.kairon, MoonDisplay.SIGNS[MoonDisplay.signIndex(moonLon)] + " "),
                WidgetRender.Segment(fonts.alegreya, MoonDisplay.dms(moonLon % 30.0)),
            )

            val voc = VocDatabase(context).getCurrentVoc()
            val now = System.currentTimeMillis()
            val statusText = when {
                voc == null -> context.getString(R.string.voc_data_expired)
                now >= voc.asptime / 60 * 60000 -> "Void until " + WidgetRender.formatTime(voc.ingress / 60 * 60000)
                else -> "Void at " + WidgetRender.formatTime(voc.asptime / 60 * 60000)
            }
            val statusLine = listOf(WidgetRender.Segment(fonts.alegreya, statusText))

            // Fit both lines into the text column with one shared scale.
            val widthDp = appWidgetManager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                .takeIf { it > 0 }?.toFloat() ?: FALLBACK_WIDTH_DP
            val availPx = WidgetRender.dpToPx(context, max(60f, widthDp - NON_TEXT_DP))
            val scale = min(
                1f,
                min(
                    availPx / WidgetRender.lineWidth(positionLine, WidgetRender.dpToPx(context, POSITION_FONT_DP)),
                    availPx / WidgetRender.lineWidth(statusLine, WidgetRender.dpToPx(context, STATUS_FONT_DP)),
                ),
            )

            views.setImageViewBitmap(
                R.id.comp_position,
                WidgetRender.lineBitmap(context, positionLine, POSITION_FONT_DP * scale),
            )
            views.setImageViewBitmap(
                R.id.comp_void_line,
                WidgetRender.lineBitmap(context, statusLine, STATUS_FONT_DP * scale),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
