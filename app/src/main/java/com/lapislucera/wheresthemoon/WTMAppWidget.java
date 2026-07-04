package com.lapislucera.wheresthemoon;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Implementation of App Widget functionality.
 *
 */
public class WTMAppWidget extends AppWidgetProvider {

    static {
        System.loadLibrary("witm");
    }

    /**
     * Converts decimal degrees to degrees, minutes, and seconds
     * @param decimal Longitude in decimal format
     * @return String representations of degrees, minutes, seconds
     */
    private static String decimal2dms( Double decimal ) {

        Integer deg, min, sec;

        deg = (int)Math.floor(decimal);
        min = (int)Math.floor( ( decimal - deg ) * 60 );
        sec = (int)Math.round( ( ( ( decimal - deg ) * 60 ) - min ) * 60 );
        return deg.toString() + "°" + min.toString() + "'" + sec.toString() + "\"";
    }

    public static void updateMoonInfo(final Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

        // variable declarations and initializations
        String      phase_file;
        Bitmap      bmp;
        RemoteViews views         = new RemoteViews(context.getPackageName(), R.layout.wtmapp_widget);
        String[]    signs         = {"q","w","e","r","t","z","u","i","o","p","ü","+"},
                    planets       = { "a","s","d","f","h","j","k","ö","ä","#"},
                    aspects       = {"<","x","c","Q","m"};
        Double      moon_lon      = witm(),
                    sun_lon       = wits();
        Integer     moon_sign     = (int)Math.floor(moon_lon/30.0),
                    label_font_sz = 14,
                    text_font_sz  = 12, phase_lon, moon_icon_id;
        Typeface    kairon        = Typeface.createFromAsset(context.getAssets(),"fonts/KaironSemiserif.ttf"),
                    alegreya      = Typeface.createFromAsset(context.getAssets(),"fonts/AlegreyaSansSC-Regular.ttf"),
                    alegreya_bold = Typeface.createFromAsset(context.getAssets(),"fonts/AlegreyaSansSC-Bold.ttf");
        @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("MMM d, h:mm a");

        // calculate the phase of the moon and set the moon icon
        if ( sun_lon > moon_lon ) {
            phase_lon = (int)Math.round( 360.0 - sun_lon + moon_lon );
        } else {
            phase_lon = (int)Math.round(moon_lon - sun_lon);
        }
        phase_lon = 18 * (int)Math.floor(phase_lon / 18) + 9;
        phase_file = "moon_" + String.format("%03d", phase_lon);
        moon_icon_id = context.getResources().getIdentifier( phase_file, "drawable", context.getPackageName());
        views.setImageViewResource(R.id.moon_icon,moon_icon_id);

        // set the moon sign and degree
        bmp = getStringBitmap(context, signs[moon_sign] + " ", kairon, text_font_sz );
        views.setImageViewBitmap(R.id.moon_sign, bmp);
        bmp = getStringBitmap(context, decimal2dms(moon_lon % 30), alegreya, text_font_sz );
        views.setImageViewBitmap(R.id.moon_degree, bmp);

        // get the VoC info from the database
        VOCDataBase voc_db = new VOCDataBase(context);
        Cursor   voc_info = voc_db.getCurrentVOC();
        String   sign     = signs[voc_info.getInt(0)],
                 aspect   = aspects[voc_info.getInt(2)],
                 planet   = planets[voc_info.getInt(3)];
        Calendar now      = Calendar.getInstance(),
                 ingress  = Calendar.getInstance(),
                 asptime  = Calendar.getInstance();

        // set the ingress and last aspect times
        ingress.setTimeInMillis(voc_info.getLong(1) * 1000);
        asptime.setTimeInMillis(voc_info.getLong(4) * 1000);

        // round times down to the nearest minute
        ingress.set(Calendar.MILLISECOND, 0);
        ingress.set(Calendar.SECOND, 0);
        asptime.set(Calendar.MILLISECOND, 0);
        asptime.set(Calendar.SECOND, 0);

        // set the labels for the last aspect and ingress sections
        bmp = getStringBitmap(context, "Last Aspect", alegreya_bold, label_font_sz );
        views.setImageViewBitmap(R.id.last_aspect_label, bmp);
        bmp = getStringBitmap(context, "Ingress", alegreya_bold, label_font_sz );
        views.setImageViewBitmap(R.id.ingress_label, bmp);

        // Display ingress sign and datetime
        bmp = getStringBitmap(context, sign + " ", kairon, text_font_sz );
        views.setImageViewBitmap(R.id.ingress_symbol, bmp);
        bmp = getStringBitmap(context, df.format(ingress.getTime()), alegreya, text_font_sz );
        views.setImageViewBitmap(R.id.ingress_time, bmp);

        // Display last aspect sign and datetime
        bmp = getStringBitmap(context, aspect + " " + planet + " ", kairon, text_font_sz );
        views.setImageViewBitmap(R.id.last_aspect_symbols, bmp);
        bmp = getStringBitmap(context, df.format(asptime.getTime()), alegreya, text_font_sz );
        views.setImageViewBitmap(R.id.last_aspect_time, bmp);

        // Is the moon void now?
        if ( now.getTimeInMillis() >= asptime.getTimeInMillis() && now.getTimeInMillis() < ingress.getTimeInMillis() ) {
            // the moon is void
            bmp = getStringBitmap(context, "The moon is void.", alegreya, text_font_sz );
            views.setImageViewBitmap(R.id.void_or_not, bmp);
        } else {
            // the moon is not void
            bmp = getStringBitmap(context, "The moon is not void.", alegreya, text_font_sz );
            views.setImageViewBitmap(R.id.void_or_not, bmp);
        }

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static Bitmap getStringBitmap(Context c, String str, Typeface tf, Integer font_size_in_dp ) {

        // convert font size in dp to pixels
        Integer font_size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, font_size_in_dp, c.getResources().getDisplayMetrics());

        // create a Paint object and set text properties
        Paint p = new Paint();
        p.setTypeface(tf);
        p.setTextSize(font_size);
        p.setAntiAlias(true);
        p.setSubpixelText(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);

        // get the width of the text
        Integer w = (int)p.measureText(str);

        Bitmap bmp = Bitmap.createBitmap(w, font_size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawText(str, w / 2, font_size / 2 + (font_size / 4), p);
        return bmp;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        final AlarmManager m = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        final PendingIntent la_svc, in_svc;
        final Calendar now  = Calendar.getInstance(),
                       la   = Calendar.getInstance(), // last aspect time
                       in   = Calendar.getInstance(); // ingress time
        final Intent   la_i = new Intent(context,WTMService.class).setAction(Intent.ACTION_EDIT),
                       in_i = new Intent(context,WTMService.class).setAction(Intent.ACTION_MAIN);
        final long la_millis, in_millis;

        // get the current VOC info
        VOCDataBase voc_db = new VOCDataBase(context);
        Cursor voc_info = voc_db.getCurrentVOC();

        // set times for last aspect and ingress; add 1 minute to ingress so update will happen just after it occurs
        la.setTimeInMillis(voc_info.getLong(4) * 1000);
        in.setTimeInMillis((voc_info.getLong(1) * 1000) + 60000);

        // round times down to the nearest minute
        now.set(Calendar.MILLISECOND, 0);
        now.set(Calendar.SECOND, 0);
        la.set(Calendar.MILLISECOND, 0);
        la.set(Calendar.SECOND, 0);
        in.set(Calendar.MILLISECOND, 0);
        in.set(Calendar.SECOND, 0);

        // if the last aspect is still in the future
        if ( la.getTimeInMillis() >= now.getTimeInMillis() ) {
            // set an alarm for the exact time of the next closing aspect
            la_svc = PendingIntent.getService(context, 0, la_i, PendingIntent.FLAG_ONE_SHOT );
            la_millis = SystemClock.elapsedRealtime() + la.getTime().getTime() - System.currentTimeMillis();
            m.setExact(AlarmManager.ELAPSED_REALTIME, la_millis, la_svc);
        }

        // set the alarm for the next ingress
        in_svc = PendingIntent.getService(context, 0, in_i, PendingIntent.FLAG_ONE_SHOT );
        in_millis = SystemClock.elapsedRealtime() + in.getTime().getTime() - System.currentTimeMillis();
        m.setExact(AlarmManager.ELAPSED_REALTIME, in_millis, in_svc);

        // There may be multiple widgets active, so update all of them
        for( int appWidgetId : appWidgetIds) {
            updateMoonInfo(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {}

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private static native double witm();
    private static native double wits();
}

