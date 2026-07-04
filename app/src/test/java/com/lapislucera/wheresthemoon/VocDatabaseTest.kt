package com.lapislucera.wheresthemoon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for the asset-database install/upgrade/query cycle,
 * running against the real bundled voc.db under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class VocDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: VocDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(VocDatabase.DATABASE_NAME).delete()
        db = VocDatabase(context)
    }

    @Test
    fun installsDatabaseFromAssetsOnFirstQuery() {
        val dbFile = context.getDatabasePath(VocDatabase.DATABASE_NAME)
        assertTrue(!dbFile.exists())
        db.getCurrentVoc(FIRST_INGRESS - 100)
        assertTrue(dbFile.exists())
        assertEquals(db.assetVersion(), db.installedVersion(dbFile))
    }

    @Test
    fun assetVersionIsTheGenerationDate() {
        // tools/vocgen stamps PRAGMA user_version with the YYYYMMDD date.
        assertTrue("user_version ${db.assetVersion()}", db.assetVersion() >= 20260704)
    }

    @Test
    fun returnsFirstIngressAfterQueryTime() {
        val voc = db.getCurrentVoc(FIRST_INGRESS - 100)
        assertNotNull(voc)
        assertEquals(FIRST_INGRESS, voc!!.ingress)
        assertTrue(voc.asptime < voc.ingress)
        assertTrue(voc.sign in 0..11)
        assertTrue(voc.aspect in 0..4)
        assertTrue(voc.planet in 0..9)
    }

    @Test
    fun ingressBoundaryIsExclusive() {
        // A query exactly at an ingress must return the following row.
        val voc = db.getCurrentVoc(FIRST_INGRESS)
        assertNotNull(voc)
        assertTrue(voc!!.ingress > FIRST_INGRESS)
    }

    @Test
    fun consecutiveRowsChainThroughTheZodiac() {
        var t = FIRST_INGRESS - 100
        var previous = db.getCurrentVoc(t)!!
        repeat(24) {
            val next = db.getCurrentVoc(previous.ingress)!!
            assertEquals(
                "sign after ${previous.sign} at ${previous.ingress}",
                (previous.sign + 1) % 12,
                next.sign,
            )
            assertTrue(next.asptime > previous.asptime)
            previous = next
        }
    }

    @Test
    fun dataCoversAtLeastFiftyYears() {
        val voc2076 = db.getCurrentVoc(NOV_2076)
        assertNotNull("data should extend to december 2076", voc2076)
    }

    @Test
    fun returnsNullWhenDataIsExpired() {
        // Query beyond the last row: the app must degrade, not crash.
        assertNull(db.getCurrentVoc(YEAR_2100))
    }

    @Test
    fun staleInstalledDatabaseIsReplacedByNewerAsset() {
        val dbFile = context.getDatabasePath(VocDatabase.DATABASE_NAME)
        // Simulate an old install: a valid SQLite file with an ancient
        // user_version and no rows for the future.
        dbFile.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null).use {
            it.execSQL("CREATE TABLE voc ( sign int, ingress int primary key, aspect int, planet int, asptime int )")
            it.execSQL("PRAGMA user_version = 1")
        }
        assertEquals(1, db.installedVersion(dbFile))

        // Any query should trigger the upgrade copy and find data again.
        val voc = db.getCurrentVoc(FIRST_INGRESS - 100)
        assertNotNull(voc)
        assertEquals(db.assetVersion(), db.installedVersion(dbFile))
    }

    private companion object {
        /** First ingress in the bundled data: 2025-07-01 21:16:22 UTC. */
        const val FIRST_INGRESS = 1751404582L
        const val NOV_2076 = 3372000000L // 2076-11-08
        const val YEAR_2100 = 4102444800L
    }
}
