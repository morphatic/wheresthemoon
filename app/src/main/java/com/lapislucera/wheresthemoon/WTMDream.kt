package com.lapislucera.wheresthemoon

import android.graphics.Color
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.ImageView
import android.widget.RelativeLayout

/**
 * Screensaver (Daydream) showing an image of the moon's current phase.
 */
class WTMDream : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val jd = Ephemeris.julianDay(System.currentTimeMillis())
        val name = MoonDisplay.phaseDrawableName(
            Ephemeris.moonLongitude(jd),
            Ephemeris.sunLongitude(jd),
        )

        val image = ImageView(this).apply {
            setImageResource(resources.getIdentifier(name, "drawable", packageName))
            setBackgroundColor(Color.BLACK)
        }
        val layout = RelativeLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            addView(image)
        }

        isInteractive = false
        isFullscreen = true
        setContentView(layout)
    }
}
