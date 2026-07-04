package com.lapislucera.wheresthemoon;

import android.graphics.Color;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.RelativeLayout;
import android.widget.ImageView;

/**
 * Displays an image of the current moon as the Daydream
 */
public class WTMDream extends DreamService {

    static {
        System.loadLibrary("witm");
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        Double  moon_lon = witm(),
                sun_lon  = wits();
        Integer phase_lon, moon_icon_id;
        String  phase_file;

        RelativeLayout rl = new RelativeLayout(this);
        ImageView      iv = new ImageView(this);

        // calculate the phase of the moon and set the moon icon
        if ( sun_lon > moon_lon ) {
            phase_lon = (int)Math.round( 360.0 - sun_lon + moon_lon );
        } else {
            phase_lon = (int)Math.round(moon_lon - sun_lon);
        }
        phase_lon = 18 * (int)Math.floor(phase_lon / 18) + 9;
        phase_file = "moon_" + String.format("%03d", phase_lon);
        moon_icon_id = getResources().getIdentifier( phase_file, "drawable", getPackageName());
        iv.setImageResource(moon_icon_id);
        iv.setBackgroundColor(Color.BLACK);

        rl.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        rl.addView(iv);

        setInteractive(false);
        setFullscreen(true);
        setContentView(rl);
    }

    private static native double witm();
    private static native double wits();

}