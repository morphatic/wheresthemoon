package com.lapislucera.wheresthemoon

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

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

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive ${intent.action}")
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WTMAppWidget::class.java))
            onUpdate(context, manager, ids)
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        private const val TAG = "WTMAppWidget"
        const val ACTION_REFRESH = "com.lapislucera.wheresthemoon.REFRESH"

        // Sized for readability on modern high-density screens (2026);
        // the original 14/12 was designed for 2015-era phones.
        private const val LABEL_FONT_SP = 22
        private const val TEXT_FONT_SP = 20

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

        fun updateMoonInfo(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.wtmapp_widget)

            val kairon = Typeface.createFromAsset(context.assets, "fonts/KaironSemiserif.ttf")
            val alegreya = Typeface.createFromAsset(context.assets, "fonts/AlegreyaSansSC-Regular.ttf")
            val alegreyaBold = Typeface.createFromAsset(context.assets, "fonts/AlegreyaSansSC-Bold.ttf")

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
                R.id.moon_sign,
                stringBitmap(context, MoonDisplay.SIGNS[MoonDisplay.signIndex(moonLon)] + " ", kairon, TEXT_FONT_SP),
            )
            views.setImageViewBitmap(
                R.id.moon_degree,
                stringBitmap(context, MoonDisplay.dms(moonLon % 30.0), alegreya, TEXT_FONT_SP),
            )

            views.setImageViewBitmap(
                R.id.last_aspect_label,
                stringBitmap(context, "Last Aspect", alegreyaBold, LABEL_FONT_SP),
            )
            views.setImageViewBitmap(
                R.id.ingress_label,
                stringBitmap(context, "Ingress", alegreyaBold, LABEL_FONT_SP),
            )

            val voc = VocDatabase(context).getCurrentVoc()
            if (voc == null) {
                // The bundled data has run out; see tools/vocgen in the repo.
                views.setImageViewBitmap(
                    R.id.void_or_not,
                    stringBitmap(context, context.getString(R.string.voc_data_expired), alegreya, TEXT_FONT_SP),
                )
                views.setImageViewBitmap(R.id.ingress_symbol, null)
                views.setImageViewBitmap(R.id.ingress_time, null)
                views.setImageViewBitmap(R.id.last_aspect_symbols, null)
                views.setImageViewBitmap(R.id.last_aspect_time, null)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            // Times rounded down to the minute, like the original app.
            val ingressMillis = voc.ingress / 60 * 60000
            val asptimeMillis = voc.asptime / 60 * 60000

            views.setImageViewBitmap(
                R.id.ingress_symbol,
                stringBitmap(context, MoonDisplay.SIGNS[voc.sign] + " ", kairon, TEXT_FONT_SP),
            )
            views.setImageViewBitmap(
                R.id.ingress_time,
                stringBitmap(context, formatTime(ingressMillis), alegreya, TEXT_FONT_SP),
            )
            views.setImageViewBitmap(
                R.id.last_aspect_symbols,
                stringBitmap(
                    context,
                    MoonDisplay.ASPECTS[voc.aspect] + " " + MoonDisplay.PLANETS[voc.planet] + " ",
                    kairon,
                    TEXT_FONT_SP,
                ),
            )
            views.setImageViewBitmap(
                R.id.last_aspect_time,
                stringBitmap(context, formatTime(asptimeMillis), alegreya, TEXT_FONT_SP),
            )

            val now = System.currentTimeMillis()
            val voidText = if (now in asptimeMillis until ingressMillis) {
                context.getString(R.string.moon_is_void)
            } else {
                context.getString(R.string.moon_not_void)
            }
            views.setImageViewBitmap(
                R.id.void_or_not,
                stringBitmap(context, voidText, alegreya, TEXT_FONT_SP),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Set exact alarms to refresh the widget when the void period
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

        private fun formatTime(unixMillis: Long): String =
            TIME_FORMAT.format(Instant.ofEpochMilli(unixMillis).atZone(ZoneId.systemDefault()))

        /** Render text to a bitmap so RemoteViews can show the custom fonts. */
        private fun stringBitmap(context: Context, str: String, typeface: Typeface, fontSizeDp: Int): Bitmap {
            val fontSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                fontSizeDp.toFloat(),
                context.resources.displayMetrics,
            ).toInt()

            val paint = Paint().apply {
                this.typeface = typeface
                textSize = fontSize.toFloat()
                isAntiAlias = true
                isSubpixelText = true
                style = Paint.Style.FILL
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            val width = max(1, paint.measureText(str).toInt())
            val bitmap = Bitmap.createBitmap(width, fontSize, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawText(str, width / 2f, fontSize / 2f + fontSize / 4f, paint)
            return bitmap
        }
    }
}
