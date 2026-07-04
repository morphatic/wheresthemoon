package com.lapislucera.wheresthemoon

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppWidgetManager

/** Robolectric tests for the compact lock-screen complication widget. */
@RunWith(RobolectricTestRunner::class)
class WTMComplicationTest {

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
    fun rendersWithoutCrashing() {
        val id = shadowManager.createWidget(WTMComplication::class.java, R.layout.wtm_complication)
        WTMComplication.updateComplication(context, manager, id)
        assertNotNull(shadowManager.getViewFor(id))
    }

    @Test
    fun survivesExpiredDatabase() {
        val dbFile = context.getDatabasePath(VocDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null).use {
            it.execSQL("CREATE TABLE voc ( sign int, ingress int primary key, aspect int, planet int, asptime int )")
            it.execSQL("INSERT INTO voc VALUES (0, 100, 0, 0, 50)")
            it.execSQL("PRAGMA user_version = 99999999")
        }
        val id = shadowManager.createWidget(WTMComplication::class.java, R.layout.wtm_complication)
        WTMComplication.updateComplication(context, manager, id)
        assertNotNull(shadowManager.getViewFor(id))
    }

    @Test
    fun refreshBroadcastUpdatesComplicationsToo() {
        val id = shadowManager.createWidget(WTMComplication::class.java, R.layout.wtm_complication)
        val intent = android.content.Intent(context, WTMAppWidget::class.java)
            .setAction(WTMAppWidget.ACTION_REFRESH)
        WTMAppWidget().onReceive(context, intent)
        assertNotNull(shadowManager.getViewFor(id))
    }
}
