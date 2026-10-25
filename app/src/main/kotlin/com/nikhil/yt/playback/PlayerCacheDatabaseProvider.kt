/*
 * Velune / Openbeats — self-contained index database for the stream cache.
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.playback

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.media3.database.DatabaseProvider

/**
 * Dedicated database for the progressive stream cache index.
 *
 * The download cache keeps using the shared "exoplayer_internal" database;
 * separating the stream cache index lets backups carry the cached offline
 * music (cache files + this index) without touching download state.
 *
 * CachedContentIndex creates and migrates its own tables on demand, so the
 * helper itself needs no schema.
 */
class PlayerCacheDatabaseProvider(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, VERSION), DatabaseProvider {

    override fun onCreate(db: SQLiteDatabase) {
        // CachedContentIndex creates its table on first use.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 only; nothing to migrate. Index tables are rebuilt on demand.
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A rolled-back build must not clash with a newer index; drop everything
        // and let the cache index rebuild (cached files become re-derivable).
        val tables = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        tables.filter { it.startsWith("ExoPlayer") || it.startsWith("androidx_media3") }
            .forEach { db.execSQL("DROP TABLE IF EXISTS \"$it\"") }
    }

    companion object {
        const val DB_NAME = "exoplayer_player_cache.db"
        const val VERSION = 1

        /** Zip entry names under which the index travels inside backups. */
        val BACKUP_DB_ENTRIES =
            listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm", "$DB_NAME-journal")

        fun isBackupIndexEntry(name: String): Boolean = name in BACKUP_DB_ENTRIES
    }
}
