package com.lapislucera.wheresthemoon;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

/**
 * Updates the WTM widget on a last aspect
 */
public class WTMService extends Service {

    @Override
    public void onCreate() { super.onCreate(); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        // update all of the widgets
        ComponentName cn = new ComponentName(this, WTMAppWidget.class);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        for ( int awid : m.getAppWidgetIds(cn) ) {
            WTMAppWidget.updateMoonInfo(this,m,awid);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
