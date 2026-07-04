package com.lapislucera.wheresthemoon

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppWidgetManager

/**
 * Integration tests for the widget provider under Robolectric: rendering
 * with the real database, fonts, and layout, plus alarm scheduling.
 */
@RunWith(RobolectricTestRunner::class)
class WTMAppWidgetTest {

    private lateinit var context: Context
    private lateinit var manager: AppWidgetManager
    private lateinit var shadowManager: ShadowAppWidgetManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(VocDatabase.DATABASE_NAME).delete()
        manager = AppWidgetManager.getInstance(context)
        shadowManager = shadowOf(manager)
    }

    @Test
    fun updateMoonInfoRendersWithoutCrashing() {
        val id = shadowManager.createWidget(WTMAppWidget::class.java, R.layout.wtmapp_widget)
        WTMAppWidget.updateMoonInfo(context, manager, id)
        assertNotNull(shadowManager.getViewFor(id))
    }

    @Test
    fun onUpdateSchedulesExactAlarms() {
        val id = shadowManager.createWidget(WTMAppWidget::class.java, R.layout.wtmapp_widget)
        WTMAppWidget().onUpdate(context, manager, intArrayOf(id))

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).scheduledAlarms
        // At least the ingress alarm is always set; the last-aspect alarm
        // is set too when the aspect is still in the future.
        assertTrue("expected 1-2 alarms, got ${scheduled.size}", scheduled.size in 1..2)

        val voc = VocDatabase(context).getCurrentVoc()!!
        val ingressAlarmMillis = (voc.ingress / 60 + 1) * 60000
        assertTrue(
            "an alarm should fire one minute after ingress",
            scheduled.any { it.triggerAtMs == ingressAlarmMillis },
        )
    }

    @Test
    fun refreshBroadcastTriggersWidgetUpdate() {
        val id = shadowManager.createWidget(WTMAppWidget::class.java, R.layout.wtmapp_widget)
        val intent = Intent(context, WTMAppWidget::class.java).setAction(WTMAppWidget.ACTION_REFRESH)
        WTMAppWidget().onReceive(context, intent)
        assertNotNull(shadowManager.getViewFor(id))
    }

    @Test
    fun widgetSurvivesExpiredDatabase() {
        // Install a database whose data is entirely in the past, then
        // render: the widget must show the expired notice, not crash.
        val dbFile = context.getDatabasePath(VocDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null).use {
            it.execSQL("CREATE TABLE voc ( sign int, ingress int primary key, aspect int, planet int, asptime int )")
            it.execSQL("INSERT INTO voc VALUES (0, 100, 0, 0, 50)")
            // Higher than any real generation stamp so the asset is not
            // reinstalled over it.
            it.execSQL("PRAGMA user_version = 99999999")
        }

        val id = shadowManager.createWidget(WTMAppWidget::class.java, R.layout.wtmapp_widget)
        WTMAppWidget.updateMoonInfo(context, manager, id)
        assertNotNull(shadowManager.getViewFor(id))

        // And scheduling alarms with expired data is a no-op, not a crash.
        WTMAppWidget.scheduleAlarms(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
