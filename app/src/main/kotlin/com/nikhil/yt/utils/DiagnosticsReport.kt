/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils

import android.content.Context
import android.os.Build
import com.nikhil.yt.BuildConfig
import com.nikhil.yt.constants.AudioQualityKey
import com.nikhil.yt.constants.AutoPoTokenEnabledKey
import com.nikhil.yt.constants.MaxSongCacheSizeKey
import com.nikhil.yt.constants.PlayerStreamClientKey
import com.nikhil.yt.constants.WebClientPoTokenEnabledKey
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diagnostics {
    const val SUPPORT_EMAIL = "heetks2607@gmail.com"

    /**
     * One-paste report for support: app/device identity, stream client probe
     * results, recent resolution events and the settings that steer playback.
     * Contains no account data.
     */
    suspend fun buildReport(context: Context): String {
        val sb = StringBuilder()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        sb.appendLine("Openbeats diagnostics report")
        sb.appendLine("----------------------------------------")
        sb.appendLine("App: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Generated: ${df.format(Date())}")
        sb.appendLine()

        sb.appendLine("[Stream client health]")
        val probes = StreamHealth.lastProbeResults
        if (probes.isEmpty()) {
            sb.appendLine("No probe results yet")
        } else {
            probes.forEach {
                sb.appendLine("${it.clientName} ${it.clientVersion}: ${it.health} (${it.elapsedMs} ms)")
            }
        }
        sb.appendLine()

        sb.appendLine("[Recent stream events]")
        val events = StreamHealth.recentEvents()
        if (events.isEmpty()) {
            sb.appendLine("None")
        } else {
            events.take(30).forEach { event ->
                val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(event.timestamp))
                sb.appendLine("$time ${event.videoId} ${event.clientName}: ${event.outcome} ${event.detail}")
            }
        }
        sb.appendLine()

        sb.appendLine("[Playback settings]")
        runCatching {
            val prefs = context.dataStore.data.first()
            sb.appendLine("autoPoToken: ${prefs[AutoPoTokenEnabledKey] ?: true}")
            sb.appendLine("preferredStreamClient: ${prefs[PlayerStreamClientKey] ?: "(default)"}")
            sb.appendLine("audioQuality: ${prefs[AudioQualityKey] ?: "(default)"}")
            sb.appendLine("maxSongCacheSize: ${prefs[MaxSongCacheSizeKey] ?: "(default)"} MB")
            sb.appendLine("webClientPoTokenEnabled: ${prefs[WebClientPoTokenEnabledKey] ?: false}")
        }.onFailure {
            sb.appendLine("settings unavailable: ${it.message}")
        }

        return sb.toString()
    }
}
