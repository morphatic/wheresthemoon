package com.lapislucera.wheresthemoon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test. Run with a device or emulator attached:
 *   gradlew connectedDebugAndroidTest
 *
 * The heavy lifting is covered by the JVM (Robolectric) tests; this just
 * proves the database install and ephemeris work on real Android.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceSmokeTest {

    @Test
    fun databaseInstallsAndAnswersOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val voc = VocDatabase(context).getCurrentVoc()
        assertNotNull("bundled voc.db should cover the present day", voc)
        assertTrue(voc!!.ingress > System.currentTimeMillis() / 1000)
    }

    @Test
    fun ephemerisProducesSaneLongitudes() {
        val jd = Ephemeris.julianDay(System.currentTimeMillis())
        val moon = Ephemeris.moonLongitude(jd)
        val sun = Ephemeris.sunLongitude(jd)
        assertTrue(moon in 0.0..360.0)
        assertTrue(sun in 0.0..360.0)
    }
}
