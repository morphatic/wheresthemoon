package com.lapislucera.wheresthemoon

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppWidgetManager

/** Robolectric tests for the two compact lock-screen tiles. */
@RunWith(RobolectricTestRunner::class)
class ComplicationsTest {

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
    fun aspectTileRendersWithoutCrashing() {
        val id = shadowManager.createWidget(WTMAspectComplication::class.java, R.layout.wtm_complication_text)
        ComplicationRenderer.renderAspectTile(context, manager, id)
        assertNotNull(shadowManager.getViewFor(id))
    }

    @Test
    fun ingressTileRendersWithoutCrashing() {
        val id = shadowManager.createWidget(WTMIngressComplication::class.java, R.layout.wtm_complication_text)
        ComplicationRenderer.renderIngressTile(context, manager, id)
        assertNotNull(shadowManager.getViewFor(id))
    }

    @Test
    fun tilesSurviveExpiredDatabase() {
        val dbFile = context.getDatabasePath(VocDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null).use {
            it.execSQL("CREATE TABLE voc ( sign int, ingress int primary key, aspect int, planet int, asptime int )")
            it.execSQL("INSERT INTO voc VALUES (0, 100, 0, 0, 50)")
            it.execSQL("PRAGMA user_version = 99999999")
        }
        val aspectId = shadowManager.createWidget(WTMAspectComplication::class.java, R.layout.wtm_complication_text)
        val ingressId = shadowManager.createWidget(WTMIngressComplication::class.java, R.layout.wtm_complication_text)
        ComplicationRenderer.renderAspectTile(context, manager, aspectId)
        ComplicationRenderer.renderIngressTile(context, manager, ingressId)
        assertNotNull(shadowManager.getViewFor(aspectId))
        assertNotNull(shadowManager.getViewFor(ingressId))
    }

    @Test
    fun refreshBroadcastUpdatesBothTiles() {
        val aspectId = shadowManager.createWidget(WTMAspectComplication::class.java, R.layout.wtm_complication_text)
        val ingressId = shadowManager.createWidget(WTMIngressComplication::class.java, R.layout.wtm_complication_text)
        val intent = Intent(context, WTMAppWidget::class.java).setAction(WTMAppWidget.ACTION_REFRESH)
        WTMAppWidget().onReceive(context, intent)
        assertNotNull(shadowManager.getViewFor(aspectId))
        assertNotNull(shadowManager.getViewFor(ingressId))
    }

    @Test
    fun onUpdateSchedulesAlarmsFromAnyTile() {
        val id = shadowManager.createWidget(WTMIngressComplication::class.java, R.layout.wtm_complication_text)
        WTMIngressComplication().onUpdate(context, manager, intArrayOf(id))
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        assert(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }

    // ── void status: color and state ───────────────────────────────────

    private val voc = VocDatabase.VocInfo(sign = 0, ingress = 2000L * 60, aspect = 3, planet = 5, asptime = 1000L * 60)

    @Test
    fun voidDuringTheVoidPeriodOnly() {
        assert(!ComplicationRenderer.isVoid(voc, 500L * 60000))
        assert(ComplicationRenderer.isVoid(voc, 1500L * 60000))
        assert(!ComplicationRenderer.isVoid(voc, 2500L * 60000))
        assert(!ComplicationRenderer.isVoid(null))
    }

    @Test
    fun colorIsGoldDuringVoidPeriod() {
        val duringVoid = 1500L * 60000 // between asptime and ingress
        assertEquals(ComplicationRenderer.VOID_COLOR, ComplicationRenderer.statusColor(voc, duringVoid))
    }

    @Test
    fun colorIsWhiteBeforeVoidBegins() {
        val beforeVoid = 500L * 60000
        assertEquals(ComplicationRenderer.NOT_VOID_COLOR, ComplicationRenderer.statusColor(voc, beforeVoid))
    }

    @Test
    fun colorIsWhiteWhenDataExpired() {
        assertEquals(ComplicationRenderer.NOT_VOID_COLOR, ComplicationRenderer.statusColor(null))
    }

    @Test
    fun colorBoundariesMatchTheMinuteRoundedDisplay() {
        // Void begins exactly at the minute-rounded asptime...
        assertEquals(ComplicationRenderer.VOID_COLOR, ComplicationRenderer.statusColor(voc, 1000L * 60000))
        // ...and ends exactly at the minute-rounded ingress.
        assertEquals(ComplicationRenderer.NOT_VOID_COLOR, ComplicationRenderer.statusColor(voc, 2000L * 60000))
    }
}
