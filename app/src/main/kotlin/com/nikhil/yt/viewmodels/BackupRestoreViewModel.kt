/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.MainActivity
import com.nikhil.yt.R
import com.nikhil.yt.constants.BACKUP_EXCLUDED_KEYS
import com.nikhil.yt.constants.PurgeStreamStatePendingKey
import com.nikhil.yt.constants.RestoredOfflineCacheKey
import com.nikhil.yt.db.InternalDatabase
import com.nikhil.yt.db.MusicDatabase
import com.nikhil.yt.db.entities.ArtistEntity
import com.nikhil.yt.db.entities.Song
import com.nikhil.yt.db.entities.SongEntity
import com.nikhil.yt.extensions.div
import com.nikhil.yt.extensions.tryOrNull
import com.nikhil.yt.extensions.zipInputStream
import com.nikhil.yt.extensions.zipOutputStream
import com.nikhil.yt.playback.MusicService
import com.nikhil.yt.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.nikhil.yt.playback.PlayerCacheDatabaseProvider
import com.nikhil.yt.utils.BackupWriter
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.Reader
import java.io.StringReader
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser

data class BackupRestoreProgressUi(
    val title: String,
    val step: String,
    val percent: Int,
    val indeterminate: Boolean,
)

internal fun readCsvRecords(reader: Reader): Sequence<List<String>> =
    sequence {
        val pushbackReader = if (reader is PushbackReader) reader else PushbackReader(reader, 1)
        val record = ArrayList<String>(8)
        val field = StringBuilder(64)
        var inQuotes = false

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }

        suspend fun SequenceScope<List<String>>.endRecord() {
            endField()
            val anyContent = record.any { it.isNotBlank() }
            if (anyContent) {
                yield(record.toList())
            }
            record.clear()
        }

        while (true) {
            val value = pushbackReader.read()
            if (value == -1) {
                if (field.isNotEmpty() || record.isNotEmpty()) {
                    endRecord()
                }
                break
            }

            val ch = value.toChar()
            when (ch) {
                '"' -> {
                    if (inQuotes) {
                        val next = pushbackReader.read()
                        if (next == '"'.code) {
                            field.append('"')
                        } else {
                            inQuotes = false
                            if (next != -1) pushbackReader.unread(next)
                        }
                    } else {
                        if (field.isEmpty()) {
                            inQuotes = true
                        } else {
                            field.append('"')
                        }
                    }
                }
                ',' -> {
                    if (inQuotes) {
                        field.append(',')
                    } else {
                        endField()
                    }
                }
                '\n' -> {
                    if (inQuotes) {
                        field.append('\n')
                    } else {
                        endRecord()
                    }
                }
                '\r' -> {
                    if (inQuotes) {
                        field.append('\r')
                    } else {
                        val next = pushbackReader.read()
                        if (next != '\n'.code && next != -1) pushbackReader.unread(next)
                        endRecord()
                    }
                }
                else -> field.append(ch)
            }
        }
    }

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    private val _backupRestoreProgress = MutableStateFlow<BackupRestoreProgressUi?>(null)
    val backupRestoreProgress: StateFlow<BackupRestoreProgressUi?> = _backupRestoreProgress.asStateFlow()

    private fun emitProgress(
        title: String,
        step: String,
        percent: Int,
        indeterminate: Boolean,
    ) {
        _backupRestoreProgress.value =
            BackupRestoreProgressUi(
                title = title,
                step = step,
                percent = percent.coerceIn(0, 100),
                indeterminate = indeterminate,
            )
    }

    fun backup(context: Context, uri: Uri, includeOfflineMusic: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = context.getString(R.string.backup_in_progress)
            try {
                var lastStep = ""

                fun emit(step: String, indeterminate: Boolean) {
                    if (step != lastStep) {
                        lastStep = step
                        // Fixed midpoint while the writer streams entries; the
                        // final step jumps to 100 when the zip completes.
                        emitProgress(
                            title = title,
                            step = step,
                            percent = 50,
                            indeterminate = indeterminate,
                        )
                    }
                }

                context.applicationContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.buffered().zipOutputStream().use { zipStream ->
                        BackupWriter.writeBackup(
                            context = context,
                            database = database,
                            zip = zipStream,
                            includeOfflineMusic = includeOfflineMusic,
                            emit = ::emit,
                        )
                    }
                } ?: throw IllegalStateException("Failed to open output stream")

                emitProgress(
                    title = title,
                    step = context.getString(R.string.backup_step_checkpoint_database),
                    percent = 100,
                    indeterminate = false,
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
                }
            } catch (exception: Exception) {
                reportException(exception)
                withContext(Dispatchers.Main) {
                    val msg = exception.message ?: context.getString(R.string.backup_create_failed)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } finally {
                _backupRestoreProgress.value = null
            }
        }
    }

    fun restore(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = context.getString(R.string.restore_in_progress)
            try {
                emitProgress(
                    title = title,
                    step = context.getString(R.string.restore_step_verifying),
                    percent = 0,
                    indeterminate = true,
                )

                val entryNames = ArrayList<String>()
                var hasDb = false
                context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.zipInputStream().use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            entryNames.add(entry.name)
                            // Tolerate backups whose entries sit inside a folder.
                            if (entry.name.substringAfterLast('/') == InternalDatabase.DB_NAME) hasDb = true
                            entry = zip.nextEntry
                        }
                    }
                }
                if (!hasDb) throw IllegalStateException("Backup missing database")

                val restoreEntries =
                    entryNames.filter { name ->
                        val base = name.substringAfterLast('/')
                        base == SETTINGS_XML_FILENAME ||
                            base == SETTINGS_FILENAME ||
                            base == InternalDatabase.DB_NAME ||
                            base == "${InternalDatabase.DB_NAME}-wal" ||
                            base == "${InternalDatabase.DB_NAME}-shm" ||
                            base == "${InternalDatabase.DB_NAME}-journal" ||
                            BackupWriter.isOfflineMusicEntry(name)
                    }

                val totalUnits = 1 + 1 + restoreEntries.size
                val unitSpan = 100f / totalUnits.coerceAtLeast(1)
                var completedUnits = 0
                var offlineRestorePrepared = false
                var restoredOfflineMusic = false

                fun emit(step: String, indeterminate: Boolean) {
                    val p = (completedUnits * unitSpan).roundToInt().coerceIn(0, 100)
                    emitProgress(title = title, step = step, percent = p, indeterminate = indeterminate)
                }

                completedUnits++
                emit(context.getString(R.string.restore_step_stopping_playback), indeterminate = true)
                runCatching { context.stopService(Intent(context, MusicService::class.java)) }
                runCatching { database.awaitIdle() }
                runCatching { database.checkpoint() }
                runCatching { database.close() }
                completedUnits++

                context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.zipInputStream().use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (name !in restoreEntries) {
                                entry = zip.nextEntry
                                continue
                            }
                            val base = name.substringAfterLast('/')
                            when (base) {
                                SETTINGS_XML_FILENAME -> {
                                    emit(context.getString(R.string.restore_step_restoring_settings), indeterminate = true)
                                    restoreSettingsFromXml(context, zip)
                                }
                                SETTINGS_FILENAME -> {
                                    emit(context.getString(R.string.restore_step_restoring_settings), indeterminate = true)
                                    val settingsDir = context.filesDir / "datastore"
                                    if (!settingsDir.exists()) settingsDir.mkdirs()
                                    (settingsDir / SETTINGS_FILENAME).outputStream().use { out ->
                                        zip.copyTo(out)
                                    }
                                }
                                InternalDatabase.DB_NAME,
                                "${InternalDatabase.DB_NAME}-wal",
                                "${InternalDatabase.DB_NAME}-shm",
                                "${InternalDatabase.DB_NAME}-journal" -> {
                                    emit(context.getString(R.string.restore_step_restoring_file, base), indeterminate = true)
                                    val dbFile = context.getDatabasePath(base)
                                    if (dbFile.exists()) {
                                        dbFile.delete()
                                    }
                                    FileOutputStream(dbFile).use { out ->
                                        zip.copyTo(out)
                                    }
                                }
                                else -> {
                                    // Offline music payload: cached song audio and
                                    // the stream-cache index database.
                                    emit(context.getString(R.string.restore_step_restoring_file, name), indeterminate = true)
                                    if (!offlineRestorePrepared) {
                                        // Drop this install's cache before mixing in
                                        // another install's cache files.
                                        runCatching { context.filesDir.resolve("exoplayer").deleteRecursively() }
                                        PlayerCacheDatabaseProvider.BACKUP_DB_ENTRIES.forEach { dbName ->
                                            runCatching { context.getDatabasePath(dbName).delete() }
                                        }
                                        offlineRestorePrepared = true
                                    }
                                    if (name.startsWith(BackupWriter.OFFLINE_DIR_ENTRY_PREFIX)) {
                                        val relative = name.removePrefix(BackupWriter.OFFLINE_DIR_ENTRY_PREFIX)
                                        if (relative.isNotBlank() && !relative.contains("..")) {
                                            val target = context.filesDir.resolve(relative)
                                            target.parentFile?.mkdirs()
                                            FileOutputStream(target).use { out ->
                                                zip.copyTo(out)
                                            }
                                        }
                                    } else if (PlayerCacheDatabaseProvider.isBackupIndexEntry(base)) {
                                        val dbFile = context.getDatabasePath(base)
                                        FileOutputStream(dbFile).use { out ->
                                            zip.copyTo(out)
                                        }
                                    }
                                    restoredOfflineMusic = true
                                }
                            }
                            completedUnits++
                            entry = zip.nextEntry
                        }
                    }
                }

                emitProgress(
                    title = title,
                    step = context.getString(R.string.restore_step_restarting),
                    percent = 100,
                    indeterminate = true,
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
                }

                try { context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete() } catch (_: Exception) {}

                // The restored database carries stream records written by an older
                // build; have the next launch purge derived stream state (format
                // rows, stream cache) before playback can rely on them. When the
                // backup included offline music, the restored cache files are
                // kept and only derived records are purged.
                context.dataStore.edit {
                    it[PurgeStreamStatePendingKey] = true
                    if (restoredOfflineMusic) it[RestoredOfflineCacheKey] = true
                }

                _backupRestoreProgress.value = null
                context.startActivity(Intent(context, MainActivity::class.java))
                exitProcess(0)
            } catch (e: Exception) {
                reportException(e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message ?: context.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                }
            } finally {
                _backupRestoreProgress.value = null
            }
        }
    }


    private suspend fun restoreSettingsFromXml(context: Context, inputStream: java.io.InputStream) {
        val content = inputStream.readBytes().toString(Charsets.UTF_8)
        if (content.isBlank()) return

        val parser = android.util.Xml.newPullParser()
        parser.setInput(StringReader(content))

        var eventType = parser.eventType
        val booleans = LinkedHashMap<String, Boolean>()
        val ints = LinkedHashMap<String, Int>()
        val longs = LinkedHashMap<String, Long>()
        val floats = LinkedHashMap<String, Float>()
        val strings = LinkedHashMap<String, String>()
        val stringSets = LinkedHashMap<String, Set<String>>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name
                val keyName = parser.getAttributeValue(null, "name")

                if (keyName != null) {
                    when (name) {
                        "boolean" -> {
                            val value = parser.getAttributeValue(null, "value")?.toBoolean()
                            if (value != null) {
                                booleans[keyName] = value
                            }
                        }
                        "int" -> {
                            val value = parser.getAttributeValue(null, "value")?.toIntOrNull()
                            if (value != null) {
                                ints[keyName] = value
                            }
                        }
                        "long" -> {
                            val value = parser.getAttributeValue(null, "value")?.toLongOrNull()
                            if (value != null) {
                                longs[keyName] = value
                            }
                        }
                        "float" -> {
                            val value = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                            if (value != null) {
                                floats[keyName] = value
                            }
                        }
                        "string" -> {
                            val value = parser.getAttributeValue(null, "value")
                            if (value != null) {
                                strings[keyName] = value
                            }
                        }
                        "string-set" -> {
                            val values = LinkedHashSet<String>()
                            while (true) {
                                val next = parser.next()
                                if (next == XmlPullParser.START_TAG && parser.name == "item") {
                                    values.add(parser.nextText())
                                    continue
                                }
                                if (next == XmlPullParser.END_TAG && parser.name == "string-set") {
                                    break
                                }
                                if (next == XmlPullParser.END_DOCUMENT) {
                                    break
                                }
                            }
                            stringSets[keyName] = values
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (
            booleans.isEmpty() &&
            ints.isEmpty() &&
            longs.isEmpty() &&
            floats.isEmpty() &&
            strings.isEmpty() &&
            stringSets.isEmpty()
        ) {
            return
        }

        // Old backups legitimately carry session keys (they were not excluded
        // before this filter existed); never apply them to this install.
        booleans.keys.removeAll(BACKUP_EXCLUDED_KEYS)
        ints.keys.removeAll(BACKUP_EXCLUDED_KEYS)
        longs.keys.removeAll(BACKUP_EXCLUDED_KEYS)
        floats.keys.removeAll(BACKUP_EXCLUDED_KEYS)
        strings.keys.removeAll(BACKUP_EXCLUDED_KEYS)
        stringSets.keys.removeAll(BACKUP_EXCLUDED_KEYS)

        context.dataStore.edit { prefs ->
            booleans.forEach { (k, v) -> prefs[booleanPreferencesKey(k)] = v }
            ints.forEach { (k, v) -> prefs[intPreferencesKey(k)] = v }
            longs.forEach { (k, v) -> prefs[longPreferencesKey(k)] = v }
            floats.forEach { (k, v) -> prefs[floatPreferencesKey(k)] = v }
            strings.forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
            stringSets.forEach { (k, v) -> prefs[stringSetPreferencesKey(k)] = v }
        }
    }
    private fun normalizeCsvHeaderCell(value: String): String =
        value
            .trim()
            .trimStart('\uFEFF')
            .lowercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")

    suspend fun importPlaylistFromCsv(context: Context, uri: Uri): ArrayList<Song> {
        val songs =
            withContext(Dispatchers.IO) {
                val out = arrayListOf<Song>()

                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val reader = PushbackReader(InputStreamReader(stream, Charsets.UTF_8), 1)
                        val iterator = readCsvRecords(reader).iterator()
                        if (!iterator.hasNext()) return@use

                        val firstRecord = iterator.next()
                        val normalizedHeader = firstRecord.map(::normalizeCsvHeaderCell)

                        val titleIndex =
                            normalizedHeader.indexOfFirst { it == "title" || it == "tracktitle" || it == "songtitle" }
                        val artistIndex =
                            normalizedHeader.indexOfFirst { it == "artist" || it == "artists" || it == "artistname" }

                        val hasHeader = titleIndex >= 0 && artistIndex >= 0
                        val resolvedTitleIndex = if (hasHeader) titleIndex else 0
                        val resolvedArtistIndex = if (hasHeader) artistIndex else 1

                        fun addFromRecord(record: List<String>) {
                            val titleRaw = record.getOrNull(resolvedTitleIndex).orEmpty()
                            val artistRaw = record.getOrNull(resolvedArtistIndex).orEmpty()

                            val title = titleRaw.trim().trimStart('\uFEFF')
                            if (title.isBlank()) return

                            val artistStr = artistRaw.trim()
                            val artists =
                                artistStr
                                    .split(';', '|')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .map { ArtistEntity(id = "", name = it) }

                            out.add(
                                Song(
                                    song = SongEntity(id = "", title = title),
                                    artists = if (artists.isEmpty()) listOf(ArtistEntity("", "")) else artists,
                                )
                            )
                        }

                        if (!hasHeader) {
                            addFromRecord(firstRecord)
                        }
                        while (iterator.hasNext()) {
                            addFromRecord(iterator.next())
                        }
                    }
                }.onFailure {
                    reportException(it)
                }

                out
            }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }

        return songs
    }

    suspend fun loadM3UOnline(
        context: Context,
        uri: Uri,
    ): ArrayList<Song> {
        val songs =
            withContext(Dispatchers.IO) {
                val out = ArrayList<Song>()

                runCatching {
                    context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                        val lines = stream.bufferedReader().readLines()
                        if (lines.firstOrNull()?.startsWith("#EXTM3U") == true) {
                            lines.forEach { rawLine ->
                                if (rawLine.startsWith("#EXTINF:")) {
                                    val artists =
                                        rawLine
                                            .substringAfter("#EXTINF:")
                                            .substringAfter(',')
                                            .substringBefore(" - ")
                                            .split(';')
                                    val title =
                                        rawLine
                                            .substringAfter("#EXTINF:")
                                            .substringAfter(',')
                                            .substringAfter(" - ")

                                    out.add(
                                        Song(
                                            song = SongEntity(id = "", title = title),
                                            artists = artists.map { ArtistEntity("", it) },
                                        )
                                    )
                                }
                            }
                        }
                    }
                }.onFailure {
                    reportException(it)
                }

                out
            }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }

        return songs
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
        const val SETTINGS_XML_FILENAME = "settings.xml"
    }
}
