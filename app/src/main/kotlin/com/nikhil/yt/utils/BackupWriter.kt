/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils

import android.content.Context
import com.nikhil.yt.constants.BACKUP_EXCLUDED_KEYS
import com.nikhil.yt.db.MusicDatabase
import com.nikhil.yt.db.InternalDatabase
import com.nikhil.yt.playback.PlayerCacheDatabaseProvider
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Core backup serialization shared by the manual backup flow and the
 * scheduled auto-backup worker.
 */
object BackupWriter {

    /** Zip entry prefix under which offline stream-cache files travel. */
    const val OFFLINE_DIR_ENTRY_PREFIX = "exoplayer/"

    fun isOfflineMusicEntry(name: String): Boolean =
        name.startsWith(OFFLINE_DIR_ENTRY_PREFIX) ||
            PlayerCacheDatabaseProvider.isBackupIndexEntry(name)

    /**
     * Writes a complete backup into [zip].
     *
     * @param includeOfflineMusic when true, also packages the progressive
     * stream cache (cached song audio + its index database) so a restore
     * keeps songs playable offline.
     */
    suspend fun writeBackup(
        context: Context,
        database: MusicDatabase,
        zip: ZipOutputStream,
        includeOfflineMusic: Boolean,
        emit: (step: String, indeterminate: Boolean) -> Unit = { _, _ -> },
    ) {
        writeSettingsXml(context, zip)
        writeDatabaseFiles(context, database, zip, emit)
        if (includeOfflineMusic) {
            writeOfflineMusic(context, zip, emit)
        }
    }

    /**
     * Serializes user preferences, excluding device-session state.
     */
    suspend fun writeSettingsXml(context: Context, zip: ZipOutputStream) {
        val prefs = context.dataStore.data.first().asMap()
        val serializer = android.util.Xml.newSerializer()
        serializer.setOutput(zip, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "VeluneBackup")
        serializer.startTag(null, "Settings")

        for ((key, value) in prefs) {
            // Session/identity state is device-local; backing it up would let a
            // later restore resurrect a retired visitor identity.
            if (key.name in BACKUP_EXCLUDED_KEYS) continue
            val tagName = when (value) {
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                is String -> "string"
                is Set<*> -> "string-set"
                else -> null
            }
            if (tagName != null) {
                serializer.startTag(null, tagName)
                serializer.attribute(null, "name", key.name)
                if (value is Set<*>) {
                    value.forEach { item ->
                        serializer.startTag(null, "item")
                        serializer.text(item.toString())
                        serializer.endTag(null, "item")
                    }
                } else {
                    serializer.attribute(null, "value", value.toString())
                }
                serializer.endTag(null, tagName)
            }
        }

        serializer.endTag(null, "Settings")
        serializer.endTag(null, "VeluneBackup")
        serializer.endDocument()
        serializer.flush()
    }

    /**
     * Checkpoints and packages the app database (settings, playlists, songs).
     */
    suspend fun writeDatabaseFiles(
        context: Context,
        database: MusicDatabase,
        zip: ZipOutputStream,
        emit: (String, Boolean) -> Unit,
    ) {
        emit(context.getString(com.nikhil.yt.R.string.backup_step_checkpoint_database), true)
        database.awaitIdle()
        database.checkpoint()

        val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
        val dbFiles = listOf(
            dbFile,
            dbFile.resolveSibling("${InternalDatabase.DB_NAME}-wal"),
            dbFile.resolveSibling("${InternalDatabase.DB_NAME}-shm"),
            dbFile.resolveSibling("${InternalDatabase.DB_NAME}-journal"),
        ).filter { it.exists() }

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        dbFiles.forEach { file ->
            emit(context.getString(com.nikhil.yt.R.string.backup_step_copying_file, file.name), true)
            zip.putNextEntry(ZipEntry(file.name))
            file.inputStream().use { input -> input.copyTo(zip, buffer.size) }
            zip.closeEntry()
        }
    }

    /**
     * Packages the progressive stream cache: cached song audio plus the cache
     * index database, so a restore keeps songs playable offline immediately.
     */
    fun writeOfflineMusic(
        context: Context,
        zip: ZipOutputStream,
        emit: (String, Boolean) -> Unit,
    ) {
        val filesDir = context.filesDir
        val cacheDir = filesDir.resolve("exoplayer")
        if (cacheDir.exists()) {
            cacheDir.walkTopDown().filter { it.isFile }.forEach { file ->
                // relativeTo(filesDir) already yields "exoplayer/<name>"
                val entryName = file.relativeTo(filesDir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }

        val dbFile = context.getDatabasePath(PlayerCacheDatabaseProvider.DB_NAME)
        listOf(
            dbFile,
            dbFile.resolveSibling("${PlayerCacheDatabaseProvider.DB_NAME}-wal"),
            dbFile.resolveSibling("${PlayerCacheDatabaseProvider.DB_NAME}-shm"),
            dbFile.resolveSibling("${PlayerCacheDatabaseProvider.DB_NAME}-journal"),
        ).filter { it.exists() }.forEach { file ->
            zip.putNextEntry(ZipEntry(file.name))
            file.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }

        emit(context.getString(com.nikhil.yt.R.string.backup_step_offline_music), true)
    }

    /** Estimated size of the offline music payload, for UI warnings. */
    fun estimateOfflineMusicSize(context: Context): Long {
        val cacheDir = context.filesDir.resolve("exoplayer")
        val dbFile = context.getDatabasePath(PlayerCacheDatabaseProvider.DB_NAME)
        val candidates = cacheDir.walkTopDown().filter { it.isFile }.toList() +
            listOf(dbFile) + listOf("-wal", "-shm", "-journal").map {
                dbFile.resolveSibling("${PlayerCacheDatabaseProvider.DB_NAME}$it")
            }
        return candidates.filter { it.exists() }.sumOf { it.length() }
    }
}
