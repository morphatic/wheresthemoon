package com.lapislucera.wheresthemoon

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/**
 * Home-screen widget showing the current moon phase, sign and degree, and
 * the void-of-course picture: the last aspect the Moon makes before
 * leaving its sign and the time it enters the next one.
 *
 * The widget refreshes on the half-hourly APPWIDGET_UPDATE cycle and also
 * sets exact alarms for the moment of the last aspect (void begins) and
 * one minute after the ingress (void ends), so the "void" line flips at
 * the right wall-clock minute. The 2015 version started a Service from
 * those alarms; modern Android forbids background service starts, so the
 * alarms now send a broadcast back to this receiver instead.
 */
class WTMAppWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i(TAG, "onUpdate for widgets ${appWidgetIds.contentToString()}")
        scheduleAlarms(context)
        for (appWidgetId in appWidgetIds) {
            updateMoonInfo(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Re-render on resize so line text is re-fitted to the new width.
        updateMoonInfo(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive ${intent.action}")
        if (intent.action == ACTION_REFRESH) {
            // Alarm fired at a void boundary: refresh every widget of both
            // kinds (home widget and lock screen complication).
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WTMAppWidget::class.java))
            onUpdate(context, manager, ids)
            WTMComplication.updateAll(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        private const val TAG = "WTMAppWidget"
        const val ACTION_REFRESH = "com.lapislucera.wheresthemoon.REFRESH"

        // Sized for readability on modern high-density screens (2026);
        // the original 14/12 was designed for 2015-era phones.
        private const val LABEL_FONT_DP = 22f
        private const val TEXT_FONT_DP = 20f

        // Horizontal space (dp) that is not available to the side columns:
        // widget padding (8+8), center column moon icon (90) + padding
        // (10+10), and a little slack for rounding.
        private const val NON_COLUMN_DP = 130f
        private const val FALLBACK_WIDGET_WIDTH_DP = 320f

        fun updateMoonInfo(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.wtmapp_widget)
            val fonts = WidgetRender.Fonts(context)

            // Current moon position and phase, computed live.
            val jd = Ephemeris.julianDay(System.currentTimeMillis())
            val moonLon = Ephemeris.moonLongitude(jd)
            val sunLon = Ephemeris.sunLongitude(jd)

            val moonIconId = context.resources.getIdentifier(
                MoonDisplay.phaseDrawableName(moonLon, sunLon),
                "drawable",
                context.packageName,
            )
            views.setImageViewResource(R.id.moon_icon, moonIconId)

            views.setImageViewBitmap(
                R.id.moon_position,
                WidgetRender.lineBitmap(
                    context,
                    listOf(
                        WidgetRender.Segment(fonts.kairon, MoonDisplay.SIGNS[MoonDisplay.signIndex(moonLon)] + " "),
                        WidgetRender.Segment(fonts.alegreya, MoonDisplay.dms(moonLon % 30.0)),
                    ),
                    TEXT_FONT_DP,
                ),
            )

            views.setImageViewBitmap(
                R.id.last_aspect_label,
                WidgetRender.lineBitmap(context, listOf(WidgetRender.Segment(fonts.alegreyaBold, "Last Aspect")), LABEL_FONT_DP),
            )
            views.setImageViewBitmap(
                R.id.ingress_label,
                WidgetRender.lineBitmap(context, listOf(WidgetRender.Segment(fonts.alegreyaBold, "Ingress")), LABEL_FONT_DP),
            )

            val voc = VocDatabase(context).getCurrentVoc()
            if (voc == null) {
                // The bundled data has run out; see tools/vocgen in the repo.
                views.setImageViewBitmap(
                    R.id.void_or_not,
                    WidgetRender.lineBitmap(
                        context,
                        listOf(WidgetRender.Segment(fonts.alegreya, context.getString(R.string.voc_data_expired))),
                        TEXT_FONT_DP,
                    ),
                )
                views.setImageViewBitmap(R.id.last_aspect_line, null)
                views.setImageViewBitmap(R.id.ingress_line, null)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            // Times rounded down to the minute, like the original app.
            val ingressMillis = voc.ingress / 60 * 60000
            val asptimeMillis = voc.asptime / 60 * 60000

            // Each info line is one bitmap (glyphs + time), and both lines
            // share one font size, chosen so the wider line fits its
            // column. Rendering them as separate per-part images let the
            // host squeeze one line but not the other, which made the
            // Ingress text look smaller than Last Aspect.
            val aspectLine = listOf(
                WidgetRender.Segment(fonts.kairon, MoonDisplay.ASPECTS[voc.aspect] + " " + MoonDisplay.PLANETS[voc.planet] + " "),
                WidgetRender.Segment(fonts.alegreya, WidgetRender.formatTime(asptimeMillis)),
            )
            val ingressLine = listOf(
                WidgetRender.Segment(fonts.kairon, MoonDisplay.SIGNS[voc.sign] + " "),
                WidgetRender.Segment(fonts.alegreya, WidgetRender.formatTime(ingressMillis)),
            )

            val widgetWidthDp = appWidgetManager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                .takeIf { it > 0 }?.toFloat() ?: FALLBACK_WIDGET_WIDTH_DP
            val columnPx = WidgetRender.dpToPx(context, max(60f, (widgetWidthDp - NON_COLUMN_DP) / 2f))
            val basePx = WidgetRender.dpToPx(context, TEXT_FONT_DP)
            val widestPx = max(WidgetRender.lineWidth(aspectLine, basePx), WidgetRender.lineWidth(ingressLine, basePx))
            val fittedFontDp = TEXT_FONT_DP * min(1f, columnPx / widestPx)

            views.setImageViewBitmap(R.id.last_aspect_line, WidgetRender.lineBitmap(context, aspectLine, fittedFontDp))
            views.setImageViewBitmap(R.id.ingress_line, WidgetRender.lineBitmap(context, ingressLine, fittedFontDp))

            val now = System.currentTimeMillis()
            val voidText = if (now in asptimeMillis until ingressMillis) {
                context.getString(R.string.moon_is_void)
            } else {
                context.getString(R.string.moon_not_void)
            }
            views.setImageViewBitmap(
                R.id.void_or_not,
                WidgetRender.lineBitmap(context, listOf(WidgetRender.Segment(fonts.alegreya, voidText)), TEXT_FONT_DP),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Set exact alarms to refresh the widgets when the void period
         * begins (last aspect) and just after it ends (ingress + 1 min).
         */
        fun scheduleAlarms(context: Context) {
            val voc = VocDatabase(context).getCurrentVoc() ?: return
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val now = System.currentTimeMillis()

            val asptimeMillis = voc.asptime / 60 * 60000
            if (asptimeMillis >= now) {
                setAlarm(context, alarmManager, asptimeMillis, requestCode = 0)
            }
            setAlarm(context, alarmManager, (voc.ingress / 60 + 1) * 60000, requestCode = 1)
        }

        private fun setAlarm(context: Context, alarmManager: AlarmManager, triggerAtMillis: Long, requestCode: Int) {
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, WTMAppWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Exact alarms need a grant on Android 12+; USE_EXACT_ALARM in
            // the manifest provides it automatically on 13+. Fall back to a
            // 10-minute window if it is somehow missing.
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60000, pending)
            }
        }
    }
}
