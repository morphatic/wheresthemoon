package com.lapislucera.wheresthemoon

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Read-only access to the bundled void-of-course database.
 *
 * The database ships in assets/databases/voc.db and is copied to the app's
 * database directory on first use. When an app update ships a regenerated
 * database, its PRAGMA user_version (set by tools/vocgen to the generation
 * date, e.g. 20260704) is higher than the installed copy's, and the copy
 * is replaced. Replaces the abandoned android-sqlite-asset-helper library
 * the 2015 app used.
 */
class VocDatabase(private val context: Context) {

    data class VocInfo(
        /** Sign the moon enters at [ingress], 0 = Aries. */
        val sign: Int,
        /** Unix time (seconds) of the moon's next sign ingress. */
        val ingress: Long,
        /** Aspect of the last aspect before ingress, index into MoonDisplay.ASPECTS. */
        val aspect: Int,
        /** Planet of the last aspect, index into MoonDisplay.PLANETS. */
        val planet: Int,
        /** Unix time (seconds) of the last aspect — start of the void period. */
        val asptime: Long,
    )

    /**
     * The VOC row for the next ingress after [nowSeconds], or null when the
     * bundled data has run out (regenerate with tools/vocgen).
     */
    fun getCurrentVoc(nowSeconds: Long = System.currentTimeMillis() / 1000): VocInfo? {
        val db = open()
        db.use {
            it.rawQuery(
                "SELECT sign, ingress, aspect, planet, asptime FROM voc " +
                    "WHERE ingress > ? ORDER BY ingress ASC LIMIT 1",
                arrayOf(nowSeconds.toString()),
            ).use { c ->
                if (!c.moveToFirst()) return null
                return VocInfo(c.getInt(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getLong(4))
            }
        }
    }

    private fun open(): SQLiteDatabase {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        installOrUpgrade(dbFile)
        return SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    private fun installOrUpgrade(dbFile: File) {
        if (dbFile.exists() && installedVersion(dbFile) >= assetVersion()) return
        dbFile.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /** PRAGMA user_version of the bundled asset database. */
    fun assetVersion(): Int =
        context.assets.open(ASSET_PATH).use { readUserVersion(it.readNBytes(HEADER_SIZE)) }

    /** PRAGMA user_version of the installed database file. */
    fun installedVersion(dbFile: File): Int =
        dbFile.inputStream().use { readUserVersion(it.readNBytes(HEADER_SIZE)) }

    /**
     * user_version lives at byte offset 60 of the SQLite file header as a
     * big-endian 32-bit integer; reading it directly avoids opening the
     * database just to compare versions.
     */
    private fun readUserVersion(header: ByteArray): Int {
        if (header.size < HEADER_SIZE) return 0
        return (header[60].toInt() and 0xFF shl 24) or
            (header[61].toInt() and 0xFF shl 16) or
            (header[62].toInt() and 0xFF shl 8) or
            (header[63].toInt() and 0xFF)
    }

    companion object {
        const val DATABASE_NAME = "voc.db"
        private const val ASSET_PATH = "databases/voc.db"
        private const val HEADER_SIZE = 64
    }
}
