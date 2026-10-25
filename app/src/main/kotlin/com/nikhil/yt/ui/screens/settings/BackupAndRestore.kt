/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.settings

import android.content.Intent
import androidx.core.net.toUri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.constants.AutoBackupEnabledKey
import com.nikhil.yt.constants.AutoBackupFolderKey
import com.nikhil.yt.constants.LastAutoBackupAtKey
import com.nikhil.yt.db.entities.Song
import com.nikhil.yt.ui.component.IconButton
import com.nikhil.yt.ui.component.SwitchPreference
import com.nikhil.yt.ui.menu.AddToPlaylistDialogOnline
import com.nikhil.yt.ui.menu.LoadingScreen
import com.nikhil.yt.ui.utils.backToMain
import com.nikhil.yt.utils.AutoBackupManager
import com.nikhil.yt.utils.BackupWriter
import com.nikhil.yt.utils.rememberPreference
import com.nikhil.yt.viewmodels.BackupRestoreViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val CSV_MIME_TYPES =
    arrayOf(
        "text/csv",
        "text/x-csv",
        "text/comma-separated-values",
        "text/x-comma-separated-values",
        "application/csv",
        "application/x-csv",
        "application/vnd.ms-excel",
        "text/plain",
        "text/*",
        "application/octet-stream",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestore(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    var importedTitle by remember { mutableStateOf("") }
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by rememberSaveable { mutableStateOf(false) }
    var isProgressStarted by rememberSaveable { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var progressPercentage by rememberSaveable { mutableIntStateOf(0) }
    
    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showBackupTypeDialog by remember { mutableStateOf(false) }
    var pendingIncludeOffline by remember { mutableStateOf(false) }
    var offlineMusicSizeBytes by remember { mutableStateOf(-1L) }

    var (autoBackupEnabled, onAutoBackupEnabledChange) = rememberPreference(
        AutoBackupEnabledKey,
        defaultValue = false
    )
    var (autoBackupFolder, onAutoBackupFolderChange) = rememberPreference(
        AutoBackupFolderKey,
        defaultValue = ""
    )
    val (lastAutoBackupAt, _) = rememberPreference(LastAutoBackupAtKey, 0L)

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri, pendingIncludeOffline)
            }
        }

    fun launchBackup(includeOfflineMusic: Boolean) {
        pendingIncludeOffline = includeOfflineMusic
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        backupLauncher.launch(
            "${context.getString(R.string.app_name)}_${
                LocalDateTime.now().format(formatter)
            }.backup"
        )
    }

    val autoBackupFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                onAutoBackupFolderChange(uri.toString())
                if (autoBackupEnabled) {
                    AutoBackupManager.ensureScheduled(context)
                }
            }
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.restore(context, uri)
            }
        }
        
    val importPlaylistFromCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val result = viewModel.importPlaylistFromCsv(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)

                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            }
        }
        
    val importM3uLauncherOnline = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = viewModel.loadM3UOnline(context, uri)
            importedSongs.clear()
            importedSongs.addAll(result)

            if (importedSongs.isNotEmpty()) {
                showChoosePlaylistDialogOnline = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
        ) {
            item {
                BackupSettingsItemStyle(
                    icon = painterResource(R.drawable.backup),
                    title = "Backup",
                    onClick = {
                        showBackupTypeDialog = true
                    }
                )
            }

            item {
                BackupSettingsItemStyle(
                    icon = painterResource(R.drawable.restore),
                    title = "Restore",
                    onClick = { restoreLauncher.launch(arrayOf("application/octet-stream")) }
                )
            }

            item {
                SwitchPreference(
                    title = { Text("Auto-backup (weekly)") },
                    description = when {
                        autoBackupFolder.isBlank() -> "Pick a folder first; backup runs once a week"
                        lastAutoBackupAt > 0L -> "Last backup: " +
                            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                .format(Date(lastAutoBackupAt))
                        else -> "Folder set — first backup runs within a week"
                    },
                    icon = { Icon(painterResource(R.drawable.backup), null) },
                    checked = autoBackupEnabled,
                    onCheckedChange = { checked ->
                        onAutoBackupEnabledChange(checked)
                        if (checked && autoBackupFolder.isNotBlank()) {
                            AutoBackupManager.ensureScheduled(context)
                        } else if (!checked) {
                            AutoBackupManager.cancel(context)
                        }
                    },
                )
            }

            item {
                BackupSettingsItemStyle(
                    icon = painterResource(R.drawable.settings),
                    title = if (autoBackupFolder.isBlank()) {
                        "Choose auto-backup folder"
                    } else {
                        val folder = runCatching {
                            DocumentFile.fromTreeUri(context, autoBackupFolder.toUri())?.name
                        }.getOrNull()
                        folder ?: "Auto-backup folder"
                    },
                    onClick = { autoBackupFolderLauncher.launch(null) }
                )
            }
            
            item {
                BackupSettingsItemStyle(
                    icon = painterResource(R.drawable.playlist_add),
                    title = "Import a \"m3u\" playlists",
                    onClick = { importM3uLauncherOnline.launch(arrayOf("audio/*")) }
                )
            }
            
            item {
                BackupSettingsItemStyle(
                    icon = painterResource(R.drawable.playlist_add),
                    title = "Import a \"csv\" playlist",
                    onClick = { importPlaylistFromCsv.launch(CSV_MIME_TYPES) }
                )
            }
        }
    }

    if (showBackupTypeDialog) {
        LaunchedEffect(Unit) {
            offlineMusicSizeBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                BackupWriter.estimateOfflineMusicSize(context)
            }
        }
        AlertDialog(
            onDismissRequest = { showBackupTypeDialog = false },
            title = { Text("What should the backup include?") },
            text = {
                Column {
                    Text("Standard — settings, playlists, songs and history. Small file.")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Offline music — also includes songs saved by listening, so they " +
                            "stay available offline right after a restore. " +
                            if (offlineMusicSizeBytes > 0L) {
                                "Estimated size: " + Formatter.formatFileSize(context, offlineMusicSizeBytes)
                            } else {
                                "This can be a large file."
                            }
                    )
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        showBackupTypeDialog = false
                        launchBackup(false)
                    }) { Text("Standard backup") }
                    TextButton(onClick = {
                        showBackupTypeDialog = false
                        launchBackup(true)
                    }) { Text("Backup with offline music") }
                    TextButton(onClick = { showBackupTypeDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        initialTextFieldValue = importedTitle,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { newVal -> isProgressStarted = newVal },
        onPercentageChange = { newPercentage -> progressPercentage = newPercentage },
        onStatusChange = { progressStatus = it }
    )

    LaunchedEffect(progressPercentage, isProgressStarted) {
        if (isProgressStarted && progressPercentage == 99) {
            delay(10000)
            if (progressPercentage == 99) {
                isProgressStarted = false
                progressPercentage = 0
            }
        }
    }

    LoadingScreen(
        isVisible = backupRestoreProgress != null || isProgressStarted,
        value = backupRestoreProgress?.percent ?: progressPercentage,
        title = backupRestoreProgress?.title,
        stepText = backupRestoreProgress?.step ?: progressStatus,
        indeterminate = backupRestoreProgress?.indeterminate ?: false,
    )
}

@Composable
private fun BackupSettingsItemStyle(
    icon: Painter,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
