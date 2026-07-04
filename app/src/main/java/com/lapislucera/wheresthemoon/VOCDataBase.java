package com.lapislucera.wheresthemoon;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

/**
 * See: https://github.com/jgilfelt/android-sqlite-asset-helper
 */
public class VOCDataBase extends SQLiteAssetHelper {

    private static final String DATBASE_NAME = "voc.db";
    private static final int DATABASE_VERSION = 1;

    public VOCDataBase(Context context) {
        super(context, DATBASE_NAME, null, DATABASE_VERSION);
    }

    public Cursor getCurrentVOC() {

        SQLiteDatabase db = getReadableDatabase();

        Cursor c = db.rawQuery("SELECT * FROM voc WHERE ingress > strftime('%s','now') ORDER BY ingress ASC LIMIT 1", null);

        c.moveToFirst();

        return c;
    }
}
