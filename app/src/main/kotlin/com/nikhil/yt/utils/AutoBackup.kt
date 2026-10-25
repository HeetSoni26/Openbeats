/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nikhil.yt.App
import com.nikhil.yt.constants.AutoBackupEnabledKey
import com.nikhil.yt.constants.AutoBackupFolderKey
import com.nikhil.yt.constants.LastAutoBackupAtKey
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipOutputStream

/**
 * Weekly safety backup into a user-chosen folder. Standard payload only
 * (settings + database): offline music is re-derivable and would make the
 * automatic archive enormous; use the manual "include offline music" option
 * for that.
 */
object AutoBackupManager {
    private const val WORK_NAME = "velune_auto_backup"
    const val AUTO_FILE_PREFIX = "Openbeats_auto_"
    const val KEEP_COUNT = 3

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? App
            val dataStore = applicationContext.dataStore
            val prefs = dataStore.data.first()
            val enabled = prefs[AutoBackupEnabledKey] ?: false
            val folderUri = prefs[AutoBackupFolderKey]
            val database = app?.database ?: return Result.retry()
            if (!enabled || folderUri.isNullOrBlank()) return Result.success()

            val tree = DocumentFile.fromTreeUri(applicationContext, folderUri.toUri())
                ?: return Result.retry()

            val temp = File(applicationContext.cacheDir, "auto_backup_${System.currentTimeMillis()}.tmp")
            try {
                ZipOutputStream(FileOutputStream(temp).buffered()).use { zip ->
                    BackupWriter.writeBackup(
                        context = applicationContext,
                        database = database,
                        zip = zip,
                        includeOfflineMusic = false,
                    )
                }

                val fileName = AutoBackupManager.AUTO_FILE_PREFIX +
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".backup"
                val target = tree.createFile("application/octet-stream", fileName)
                    ?: return Result.retry()
                applicationContext.contentResolver.openOutputStream(target.uri)?.use { out ->
                    temp.inputStream().use { it.copyTo(out) }
                } ?: return Result.retry()

                // Keep only the newest archives.
                tree.listFiles()
                    .filter {
                        it.name?.startsWith(AutoBackupManager.AUTO_FILE_PREFIX) == true &&
                            it.name?.endsWith(".backup") == true
                    }
                    .sortedByDescending { it.lastModified() }
                    .drop(AutoBackupManager.KEEP_COUNT)
                    .forEach { runCatching { it.delete() } }

                dataStore.edit { it[LastAutoBackupAtKey] = System.currentTimeMillis() }
                Result.success()
            } finally {
                temp.delete()
            }
        } catch (e: Exception) {
            reportException(e)
            Result.retry()
        }
    }
}
