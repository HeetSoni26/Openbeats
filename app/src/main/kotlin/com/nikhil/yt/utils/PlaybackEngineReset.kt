/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import com.nikhil.yt.constants.InnerTubeCookieKey
import com.nikhil.yt.constants.VisitorDataAtKey
import com.nikhil.yt.constants.VisitorDataKey
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.playback.PlayerConnection
import com.nikhil.yt.utils.potoken.WebPoTokenGenerator
import kotlinx.coroutines.flow.first

/**
 * One-tap "make everything fresh": clears client bans and cached URLs, forces a
 * brand-new BotGuard token session, rotates the device identity when anonymous,
 * and re-prepares the current song if playback was stuck. The stronger version
 * of reopening the app — the queue stays where it is.
 */
object PlaybackEngineReset {

    suspend fun reset(context: Context, playerConnection: PlayerConnection? = null) {
        // 1. Drop all in-memory playback state (bans, failure trackers, URLs).
        YTPlayerUtils.resetPlaybackState()

        // 2. Force a brand-new BotGuard session; next token mint is from scratch.
        WebPoTokenGenerator.resetSession()

        // 3. Rotate the device identity when anonymous. Logged-in identities are
        // account-bound and must not be rotated here.
        val cookie = context.dataStore.data.first()[InnerTubeCookieKey].orEmpty()
        val loggedIn = "SAPISID" in cookie
        if (!loggedIn) {
            runCatching {
                val fresh = YouTube.visitorData().getOrNull()
                if (!fresh.isNullOrBlank()) {
                    YouTube.visitorData = fresh
                    context.dataStore.edit {
                        it[VisitorDataKey] = fresh
                        it[VisitorDataAtKey] = System.currentTimeMillis()
                    }
                }
            }
        }

        // 4. Re-prepare the current song only when playback is actually stuck.
        val player = playerConnection?.player
        if (player != null &&
            player.currentMediaItem != null &&
            (player.playbackState == Player.STATE_IDLE || player.playerError != null)
        ) {
            runCatching { playerConnection.service.retryCurrentFromFreshStream() }
        }
    }
}
