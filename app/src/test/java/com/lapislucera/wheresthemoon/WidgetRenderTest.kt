package com.lapislucera.wheresthemoon

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Geometry tests for the alpha-silhouette moon used on the lock screen. */
@RunWith(RobolectricTestRunner::class)
class WidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun alphaAt(bmp: Bitmap, fx: Float, fy: Float): Int =
        (bmp.getPixel((bmp.width * fx).toInt(), (bmp.height * fy).toInt()) ushr 24) and 0xFF

    private fun silhouette(elongation: Double): Bitmap =
        WidgetRender.phaseSilhouetteBitmap(context, elongation, 48f)

    @Test
    fun fullMoonIsFullyLit() {
        val bmp = silhouette(180.0)
        assertTrue(alphaAt(bmp, 0.5f, 0.5f) > 200)
        assertTrue(alphaAt(bmp, 0.25f, 0.5f) > 200)
        assertTrue(alphaAt(bmp, 0.75f, 0.5f) > 200)
    }

    @Test
    fun newMoonIsFaintEverywhere() {
        val bmp = silhouette(0.0)
        assertTrue(alphaAt(bmp, 0.5f, 0.5f) in 30..120)
        assertTrue(alphaAt(bmp, 0.75f, 0.5f) in 30..120)
    }

    @Test
    fun firstQuarterLitOnRight() {
        val bmp = silhouette(90.0)
        assertTrue(alphaAt(bmp, 0.75f, 0.5f) > 200) // right lit
        assertTrue(alphaAt(bmp, 0.25f, 0.5f) < 120) // left shadow
    }

    @Test
    fun lastQuarterLitOnLeft() {
        val bmp = silhouette(270.0)
        assertTrue(alphaAt(bmp, 0.25f, 0.5f) > 200) // left lit
        assertTrue(alphaAt(bmp, 0.75f, 0.5f) < 120) // right shadow
    }

    @Test
    fun waningGibbousMostlyLitWithShadowOnRight() {
        val bmp = silhouette(225.0)
        assertTrue(alphaAt(bmp, 0.4f, 0.5f) > 200)
        assertTrue(alphaAt(bmp, 0.95f, 0.5f) < 120 || alphaAt(bmp, 0.9f, 0.5f) < 120)
    }

    @Test
    fun cornersOutsideDiscAreTransparent() {
        val bmp = silhouette(180.0)
        assertTrue(alphaAt(bmp, 0.02f, 0.02f) == 0)
        assertTrue(alphaAt(bmp, 0.98f, 0.98f) == 0)
    }
}
