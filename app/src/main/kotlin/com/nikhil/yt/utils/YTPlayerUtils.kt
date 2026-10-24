/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import com.nikhil.yt.constants.AudioQuality
import com.nikhil.yt.constants.PlayerStreamClient
import com.nikhil.yt.innertube.pages.NewPipeUtils
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.YouTubeClient
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.IOS
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.nikhil.yt.innertube.models.response.PlayerResponse
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.ANDROID_TESTSUITE
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.ANDROID_UNPLUGGED
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.IPADOS
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.IOS_MUSIC
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.MOBILE
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.TVHTML5
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.VISIONOS
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.WEB
import com.nikhil.yt.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.nikhil.yt.utils.potoken.WebPoTokenGenerator
import com.nikhil.yt.utils.potoken.WebPoTokenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L

    @Volatile private var streamClientPair: Pair<java.net.Proxy?, OkHttpClient>? = null

    private fun currentStreamClient(): OkHttpClient {
        val current = YouTube.streamProxy
        streamClientPair?.let { (proxy, client) ->
            if (proxy == current) return client
        }
        val client = OkHttpClient.Builder()
            .proxy(current)
            .build()
        streamClientPair = current to client
        return client
    }
    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.nikhil.yt.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX
    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     *
     * Ordered by real-world reliability (verified against the player endpoint):
     * clients that still return directly playable stream URLs without a PO token
     * come first; clients that are currently bot-gated, soft-blocked or return
     * formats without usable URLs come last. Keep this order up to date as
     * YouTube's client enforcement changes.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        IOS,
        ANDROID_VR_1_61_48,
        ANDROID_VR_NO_AUTH,
        ANDROID_VR_1_43_32,
        IOS_MUSIC,
        ANDROID_MUSIC,
        MOBILE,
        IPADOS,
        ANDROID_CREATOR,
        ANDROID_TESTSUITE,
        ANDROID_UNPLUGGED,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        WEB,
        WEB_CREATOR,
        WEB_REMIX
    )
    private data class CachedStreamUrl(
        val url: String,
        val expiresAtMs: Long,
    )

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()

    fun invalidateCachedStreamUrls(videoId: String) {
        val prefix = "$videoId:"
        streamUrlCache.keys.removeIf { it.startsWith(prefix) }
    }

    fun markStreamClientFailed(videoId: String, clientKey: String?, httpStatusCode: Int?) {
        if (httpStatusCode != 403 && httpStatusCode != 429) return
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return
        failedStreamClientsUntil[normalizedClientKey] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
    }

    private fun isStreamClientTemporarilyBlocked(videoId: String, clientKey: String?): Boolean {
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return false

        val until = failedStreamClientsUntil[normalizedClientKey] ?: return false
        if (until <= System.currentTimeMillis()) {
            failedStreamClientsUntil.remove(normalizedClientKey)
            return false
        }
        return true
    }


    fun markPreferredClientFailed(videoId: String, client: PlayerStreamClient, httpStatusCode: Int?) {
        markStreamClientFailed(videoId, client.name, httpStatusCode)
    }
    private fun normalizeStreamClientKey(clientKey: String?): String {
        return clientKey?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.US).orEmpty()
    }

    private fun buildFailedClientKey(videoId: String, clientKey: String): String {
        return "$videoId:${normalizeStreamClientKey(clientKey)}"
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )
    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT] (or IOS when the
     * main client is soft-blocked).
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        // if provided, this preference overrides ConnectivityManager.isActiveNetworkMetered
        networkMetered: Boolean? = null,
        avoidCodecs: Set<String> = emptySet(),
    ): Result<PlaybackData> = runCatching {
        val attempts =
            when (audioQuality) {
                AudioQuality.HIGHEST -> listOf(AudioQuality.HIGHEST, AudioQuality.HIGH)
                AudioQuality.AUTO -> listOf(AudioQuality.AUTO, AudioQuality.HIGH)
                else -> listOf(audioQuality)
            }.distinct()

        var lastError: Throwable? = null
        var visitorRotated = false

        suspend fun runAttempts(): PlaybackData? {
            var innerError: Throwable? = null
            for (attempt in attempts) {
                val attemptResult =
                    runCatching {
                        playerResponseForPlaybackOnce(
                            videoId = videoId,
                            playlistId = playlistId,
                            audioQuality = attempt,
                            connectivityManager = connectivityManager,
                            preferredStreamClient = preferredStreamClient,
                            networkMetered = networkMetered,
                            avoidCodecs = avoidCodecs,
                        )
                    }
                if (attemptResult.isSuccess) return attemptResult.getOrThrow()
                innerError = attemptResult.exceptionOrNull()
            }
            lastError = innerError
            return null
        }

        var playbackData = runAttempts()

        // When every client was bot-gated, rotate the visitor identity once (only for
        // anonymous sessions) and retry: YouTube's bot-gating is partly per-session.
        if (playbackData == null && !visitorRotated && YouTube.cookie == null &&
            lastError is PlaybackException && isBotDetectionException(lastError as PlaybackException)
        ) {
            val newVisitor = YouTube.visitorData().getOrNull()
            if (newVisitor != null && newVisitor != YouTube.visitorData) {
                Timber.tag(logTag).w("All clients bot-gated; rotating visitor identity and retrying once")
                YouTube.visitorData = newVisitor
                visitorRotated = true
                playbackData = runAttempts()
            }
        }

        if (playbackData != null) return@runCatching playbackData
        throw lastError ?: IllegalStateException("Failed to resolve stream")
    }

    private suspend fun playerResponseForPlaybackOnce(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
        avoidCodecs: Set<String>,
    ): PlaybackData {
        Timber.tag(logTag).i("Fetching player response for videoId: $videoId, playlistId: $playlistId")

        // The signature timestamp requires NewPipe to download the YouTube watch page and
        // player JS, which is slow. Only the WEB-family stream clients need it, so resolve
        // it lazily instead of paying that cost on every song.
        var signatureTimestamp: Int? = null
        fun ensureSignatureTimestamp(): Int? {
            if (signatureTimestamp == null) {
                signatureTimestamp = getSignatureTimestampOrNull(videoId)
                Timber.tag(logTag).v("Signature timestamp: $signatureTimestamp")
            }
            return signatureTimestamp
        }

        val isLoggedIn = YouTube.cookie != null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        Timber.tag(logTag).v("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"} (sessionId=${sessionId.orEmpty()})")

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null

        // Keep the declared order even when logged in: the login-supported clients are
        // currently the ones most aggressively bot-gated, so preferring them first would
        // burn several doomed requests before reaching a client that actually streams.
        // Login cookies are still sent for loginSupported clients when they are reached.
        val orderedFallbackClients = STREAM_FALLBACK_CLIENTS.toList().distinct()

        val preferredYouTubeClient =
            when (preferredStreamClient) {
                PlayerStreamClient.ANDROID_VR -> ANDROID_VR_1_61_48
                PlayerStreamClient.WEB_REMIX -> WEB_REMIX
                PlayerStreamClient.IOS -> IOS
                PlayerStreamClient.MOBILE -> ANDROID_MUSIC
                PlayerStreamClient.TVHTML5 -> TVHTML5_SIMPLY_EMBEDDED_PLAYER
                PlayerStreamClient.ANDROID_MUSIC -> ANDROID_MUSIC
            }

        // WEB_REMIX metadata is the most complete (loudnessDb, premium formats), but it is
        // soft-blocked without a valid PO token, so it is best-effort; IOS is the reliable
        // no-auth fallback for metadata.
        val metadataClient = MAIN_CLIENT

        Timber.tag(logTag).i("Fetching metadata response using client: ${metadataClient.clientName}")
        var metadataClientUsed = metadataClient
        // Metadata does not require a signature timestamp; pass null to avoid the
        // expensive NewPipe player-JS fetch unless a stream client needs it later.
        val metadataPlayerResponse =
            runCatching {
                YouTube.player(videoId, playlistId, metadataClient, null).getOrThrow()
            }.getOrNull() ?: run {
                Timber.tag(logTag).w("Metadata from ${metadataClient.clientName} unavailable, falling back to IOS")
                metadataClientUsed = IOS
                runCatching {
                    YouTube.player(videoId, playlistId, IOS, null).getOrThrow()
                }.getOrNull()
            }
        val audioConfig = metadataPlayerResponse?.playerConfig?.audioConfig
        val videoDetails = metadataPlayerResponse?.videoDetails
        val playbackTracking = metadataPlayerResponse?.playbackTracking
        val expectedDurationMs = videoDetails?.lengthSeconds?.toLongOrNull()?.takeIf { it > 0 }?.times(1000L)

        val streamClients =
            buildList {
                add(preferredYouTubeClient)
                addAll(orderedFallbackClients)
                if (preferredYouTubeClient != MAIN_CLIENT) add(MAIN_CLIENT)
            }.distinct().filterNot { client ->
                val blocked = isStreamClientTemporarilyBlocked(videoId, client.clientName)
                if (blocked) {
                    Timber.tag(logTag).w("Temporarily blocked stream client for $videoId: ${client.clientName}")
                }
                blocked
            }.let { list ->
                // Self-healing order: keep the preferred client first, then sort the
                // remaining fallbacks by their last probe result so clients that are
                // currently truncated/bot-gated are not burned on every song.
                if (StreamHealth.clientRanks.isEmpty()) {
                    list
                } else {
                    listOfNotNull(list.firstOrNull()) + list.drop(1).sortedBy { StreamHealth.rankFor(it.clientName) }
                }
            }

        val botDetectedClients = mutableSetOf<String>()

        for ((index, client) in streamClients.withIndex()) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null
            streamPlayerResponse = null

            Timber.tag(logTag).v(
                "Trying ${if (client == MAIN_CLIENT) "MAIN_CLIENT" else "fallback client"} ${index + 1}/${streamClients.size}: ${client.clientName}"
            )

            if (client != MAIN_CLIENT && client.loginRequired && !isLoggedIn) {
                Timber.tag(logTag).w("Skipping client ${client.clientName} - requires login but user is not logged in")
                continue
            }

            // WEB-family clients are soft-blocked/truncated without a real BotGuard PO
            // token; generate one (fail-open) when automatic attestation is enabled.
            var webPoToken: WebPoTokenResult? = null
            if (StreamHealth.autoPoTokenEnabled && StreamClientUtils.isWebClient(client.clientName) && !sessionId.isNullOrBlank()) {
                webPoToken = runCatching {
                    WebPoTokenGenerator.getWebClientPoToken(videoId, sessionId!!)
                }.onFailure {
                    Timber.tag(logTag).w(it, "poToken generation failed for ${client.clientName}, continuing without")
                }.getOrNull()
            }

            streamPlayerResponse =
                if (client == metadataClientUsed && !client.useSignatureTimestamp) {
                    // Metadata was fetched without a signature timestamp, so only reuse it
                    // for clients that don't need one.
                    metadataPlayerResponse
                } else {
                    Timber.tag(logTag).i("Fetching player response for fallback client: ${client.clientName}")
                    val ts = if (client.useSignatureTimestamp) ensureSignatureTimestamp() else null
                    YouTube.player(videoId, playlistId, client, ts, webPoToken?.playerRequestPoToken).getOrNull()
                }

            if (streamPlayerResponse == null) continue

            if (streamPlayerResponse.playabilityStatus.status != "OK") {
                val reason = streamPlayerResponse.playabilityStatus.reason.orEmpty()
                val isBotDetection = isBotDetectionError(reason)
                Timber.tag(logTag).w(
                    "Player response status not OK: ${streamPlayerResponse.playabilityStatus.status}, reason: $reason, botDetection: $isBotDetection"
                )
                StreamHealth.record(
                    videoId,
                    client.clientName,
                    if (isBotDetection) "BOT_GATED" else "UNAVAILABLE",
                    reason,
                )
                if (isBotDetection) {
                    botDetectedClients.add(client.clientName)

                    failedStreamClientsUntil[normalizeStreamClientKey(client.clientName)] =
                        System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
                }
                continue
            }

            val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
            val candidates =
                selectAudioFormatCandidates(
                    streamPlayerResponse,
                    audioQuality,
                    isMetered,
                    avoidCodecs = avoidCodecs,
                )

            if (candidates.isEmpty()) continue

            var selectedFormat: PlayerResponse.StreamingData.Format? = null
            var selectedUrl: String? = null

            for (candidate in candidates.asSequence().take(6)) {
                if (isLoggedIn && expectedDurationMs != null && isLikelyPreview(candidate, expectedDurationMs)) continue
                val cacheKey = buildCacheKey(videoId, candidate.itag)
                val cached = streamUrlCache[cacheKey]
                var candidateUrl: String?
                val fromCache: Boolean
                if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
                    candidateUrl = cached.url
                    fromCache = true
                } else {
                    candidateUrl = findUrlOrNull(candidate, videoId, client, webPoToken)
                    fromCache = false
                }
                if (candidateUrl == null) continue

                val failCode = validateStatus(candidateUrl, client.userAgent, candidate.contentLength)
                if (failCode != null) {
                    if (fromCache) {
                        // the cached url may simply have been killed server-side; mint a
                        // fresh one and validate that before condemning the client
                        StreamHealth.record(videoId, client.clientName, "STALE_URL", "itag=${candidate.itag} http=$failCode")
                        streamUrlCache.remove(cacheKey)
                        candidateUrl = findUrlOrNull(candidate, videoId, client, webPoToken)
                        if (candidateUrl == null) continue
                        if (validateStatus(candidateUrl, client.userAgent, candidate.contentLength) != null) continue
                    } else {
                        StreamHealth.record(videoId, client.clientName, "VALIDATE_FAILED", "itag=${candidate.itag} http=$failCode")
                        if (failCode == 403 || failCode == 429) {
                            // server-side truncation/blocking of this client's streams;
                            // skip it for a while instead of dying mid-song again
                            markStreamClientFailed(videoId, client.clientName, failCode)
                        }
                        continue
                    }
                }

                selectedFormat = candidate
                selectedUrl = candidateUrl
                break
            }

            if (selectedFormat == null || selectedUrl == null) continue

            format = selectedFormat
            streamUrl = selectedUrl
            streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds

            if (streamExpiresInSeconds == null) {
                streamPlayerResponse = null
                continue
            }

            Timber.tag(logTag).i("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")
            Timber.tag(logTag).v("Stream expires in: $streamExpiresInSeconds seconds")
            Timber.tag(logTag).i("Stream validated successfully with client: ${client.clientName}")
            StreamHealth.record(videoId, client.clientName, "OK", "itag=${format.itag} bitrate=${format.bitrate}")
            break
        }

        if (streamPlayerResponse == null) {
            if (botDetectedClients.isNotEmpty()) {
                Timber.tag(logTag).e("Bot detection triggered on clients: $botDetectedClients - all clients failed")
                throw PlaybackException(
                    "Sign in to confirm you're not a bot",
                    null,
                    PlaybackException.ERROR_CODE_REMOTE_ERROR
                )
            }
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find suitable format for quality: $audioQuality. Available formats from last client: ${streamPlayerResponse.streamingData?.adaptiveFormats?.filter { it.isAudio }?.map { "${it.mimeType} @ ${it.bitrate}bps (itag: ${it.itag})" }}")
            throw Exception("Could not find format for quality: $audioQuality")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url for format: ${format.mimeType}, itag: ${format.itag}")
            throw Exception("Could not find stream url")
        }

        Timber.tag(logTag).i("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")

        // Drop expired entries so the cache cannot grow unbounded during a long session.
        val now = System.currentTimeMillis()
        streamUrlCache.values.removeIf { it.expiresAtMs <= now }
        streamUrlCache[buildCacheKey(videoId, format.itag)] =
            CachedStreamUrl(
                url = streamUrl,
                expiresAtMs = now + (streamExpiresInSeconds * 1000L),
            )

        return PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }
    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).i("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = MAIN_CLIENT)
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        // optional override from user preference; if non-null, use this instead of ConnectivityManager
        networkMetered: Boolean? = null,
        avoidCodecs: Set<String> = emptySet(),
    ): PlayerResponse.StreamingData.Format? {
        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
        return selectAudioFormatCandidates(
            playerResponse,
            audioQuality,
            isMetered,
            avoidCodecs = avoidCodecs,
        ).firstOrNull()
    }

    private fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
        avoidCodecs: Set<String> = emptySet(),
    ): List<PlayerResponse.StreamingData.Format> {
        Timber.tag(logTag).i("Finding format with audioQuality: $audioQuality, network metered: $networkMetered")

        val audioFormats =
            playerResponse.streamingData?.adaptiveFormats
                ?.asSequence()
                ?.filter { it.isAudio && it.bitrate > 0 }
                ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
                ?.filter { format ->
                    val codec = extractCodec(format.mimeType)?.lowercase()
                    codec == null || codec !in avoidCodecs
                }
                ?.toList()
                .orEmpty()

        if (audioFormats.isEmpty()) return emptyList()

        val effectiveQuality =
            when (audioQuality) {
                AudioQuality.AUTO -> if (networkMetered) AudioQuality.HIGH else AudioQuality.HIGHEST
                else -> audioQuality
            }

        val targetBitrateBps =
            when (effectiveQuality) {
                AudioQuality.LOW -> 70_000
                AudioQuality.HIGH -> 160_000
                AudioQuality.HIGHEST -> 320_000
                AudioQuality.AUTO -> null
            }

        val preferHigher =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenBy { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val candidates =
            if (targetBitrateBps == null) {
                audioFormats.sortedWith(preferHigher)
            } else {
                val belowOrEqual = audioFormats.filter { it.bitrate <= targetBitrateBps }
                if (belowOrEqual.isNotEmpty()) {
                    belowOrEqual.sortedWith(preferHigher)
                } else {
                    val aboveOrEqual = audioFormats.filter { it.bitrate >= targetBitrateBps }
                    if (aboveOrEqual.isNotEmpty()) aboveOrEqual.sortedWith(preferLowerAboveTarget)
                    else audioFormats.sortedWith(preferHigher)
                }
            }

        Timber.tag(logTag)
            .v(
                "Available audio formats: ${
                    candidates.take(12).map {
                        val codec = extractCodec(it.mimeType)
                        val direct = if (it.url != null) "direct" else "cipher"
                        "${it.mimeType} ($direct, codec=${codec ?: "unknown"}) @ ${it.bitrate}bps"
                    }
                }"
            )

        return candidates
    }

    private fun extractCodec(mimeType: String): String? {
        val match = Regex("""codecs="([^"]+)"""").find(mimeType) ?: return null
        return match.groupValues.getOrNull(1)?.split(",")?.firstOrNull()?.trim()
    }

    private fun codecRank(codec: String?): Int =
        when {
            codec.isNullOrBlank() -> 0
            codec.contains("opus", ignoreCase = true) -> 3
            codec.contains("mp4a", ignoreCase = true) -> 2
            else -> 1
        }
    private fun isLikelyPreview(
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long,
    ): Boolean {
        val approx = format.approxDurationMs?.toLongOrNull() ?: return false
        if (expectedDurationMs < 90_000L) return false
        return approx in 1L..(minOf(90_000L, (expectedDurationMs * 9L) / 10L))
    }
    /**
     * Checks if the stream url returns a successful status.
     * Returns null when the url is likely to work, or the HTTP code of the first failing probe.
     *
     * Besides probing the start of the stream, a range near the end of the file is probed when
     * the content length is known: YouTube sometimes serves unattested client streams truncated
     * to the first ~1 MB and 403s everything beyond, which would otherwise only surface as
     * playback stopping mid-song.
     */
    private fun validateStatus(url: String, userAgent: String, contentLength: Long? = null): Int? {
        Timber.tag(logTag).v("Validating stream URL status")
        try {
            val httpUrl = url.toHttpUrlOrNull()
            val clientParam = httpUrl?.queryParameter("c")?.trim().orEmpty()

            val resolvedUserAgent = StreamClientUtils.resolveUserAgent(clientParam).ifEmpty { userAgent }
            val originReferer = StreamClientUtils.resolveOriginReferer(clientParam)

            val probeRanges = buildList {
                if (StreamClientUtils.isWebClient(clientParam)) {
                    add("bytes=0-0")
                    add("bytes=262144-262145")
                    add("bytes=1048576-1048577")
                } else {
                    add("bytes=0-0")
                }
                contentLength?.takeIf { it > 4096L }?.let { cl ->
                    val endOffset = cl - 1024L
                    add("bytes=$endOffset-${endOffset + 1}")
                }
            }

            for (range in probeRanges) {
                val rangeRequest =
                    okhttp3.Request.Builder()
                        .get()
                        .header("User-Agent", resolvedUserAgent)
                        .header("Range", range)
                        .apply {
                            originReferer.origin?.let { header("Origin", it) }
                            originReferer.referer?.let { header("Referer", it) }
                        }.url(url)
                        .build()

                val code = currentStreamClient().newCall(rangeRequest).execute().use { response -> response.code }
                if (code == 403) return 403
                if (code !in 200..399 && code != 416) return code
            }

            return null
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return -1
    }
    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        Timber.tag(logTag).i("Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(logTag).i("Signature timestamp obtained: $it") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get signature timestamp")
                reportException(it)
            }
            .getOrNull()
    }
    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions.
     * Also patches cver to match the client version and appends the streaming PO token
     * when a real (BotGuard-generated) one is available.
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
        webPoToken: WebPoTokenResult? = null,
    ): String? {
        Timber.tag(logTag).i("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        var url = NewPipeUtils.getStreamUrl(format, videoId, client)
            .onSuccess { Timber.tag(logTag).i("Stream URL obtained successfully") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get stream URL")
                reportException(it)
            }
            .getOrNull() ?: return null

        // Patch cver in the URL to match the client we actually used
        if (client != null) {
            url = StreamClientUtils.patchClientVersion(url, client.clientVersion)
        }

        webPoToken?.let {
            // Replace (not just append): NewPipe's URL path may have already attached a
            // manually extracted pot, and the freshly minted automatic token must win.
            url = StreamClientUtils.replacePoToken(url, it.streamingDataPoToken)
        }

        return url
    }

    private fun buildCacheKey(videoId: String, itag: Int): String {
        return "$videoId:$itag"
    }

    private fun isBotDetectionError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "sign in" in lower ||
                "bot" in lower ||
                "confirm" in lower && "not a" in lower ||
                "verify" in lower && "human" in lower
    }

    fun isBotDetectionException(error: PlaybackException): Boolean {
        val message = error.message.orEmpty()
        if (isBotDetectionError(message)) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (isBotDetectionError(cause.message.orEmpty())) return true
            cause = cause.cause
        }
        return false
    }

    //region Self-healing client probe
    /**
     * Known-good music videos used to probe clients. Probing only needs ONE playable
     * video; the list exists in case one of them becomes unavailable.
     */
    private val PROBE_VIDEO_IDS = arrayOf("JGwWNGJdvx8", "dQw4w9WgXcQ")

    data class ClientProbeResult(
        val clientName: String,
        val clientVersion: String,
        val health: StreamHealth.ClientHealth,
        val elapsedMs: Long,
    )

    /**
     * Runs every fallback client against a known-good video and reports whether it can
     * still serve a complete (non-truncated) stream today. Results feed the dynamic
     * fallback ordering and the Playback diagnostics screen.
     */
    suspend fun probeStreamClients(): List<ClientProbeResult> = withContext(Dispatchers.IO) {
        val videoId = PROBE_VIDEO_IDS.firstOrNull { probeId ->
            runCatching { YouTube.player(probeId, null, MAIN_CLIENT).getOrThrow() }.isSuccess
        } ?: return@withContext emptyList()

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        STREAM_FALLBACK_CLIENTS.map { client ->
            val start = System.currentTimeMillis()
            val health = runCatching { probeSingleClient(videoId, client, signatureTimestamp) }
                .getOrElse { StreamHealth.ClientHealth.ERROR }
            ClientProbeResult(client.clientName, client.clientVersion, health, System.currentTimeMillis() - start)
        }.also { results ->
            StreamHealth.updateRanksFromEntries(
                results.map { StreamHealth.ClientHealthEntry(it.clientName, it.clientVersion, it.health, it.elapsedMs) }
            )
            results.forEach { StreamHealth.record(videoId, it.clientName, "PROBE_${it.health.name}", "took ${it.elapsedMs}ms") }
        }
    }

    private suspend fun probeSingleClient(
        videoId: String,
        client: YouTubeClient,
        signatureTimestamp: Int?,
    ): StreamHealth.ClientHealth {
        var webPoToken: WebPoTokenResult? = null
        val sessionId = YouTube.dataSyncId ?: YouTube.visitorData
        if (StreamHealth.autoPoTokenEnabled && StreamClientUtils.isWebClient(client.clientName) && !sessionId.isNullOrBlank()) {
            webPoToken = runCatching { WebPoTokenGenerator.getWebClientPoToken(videoId, sessionId) }.getOrNull()
        }

        val response = YouTube.player(
            videoId,
            null,
            client,
            if (client.useSignatureTimestamp) signatureTimestamp else null,
            webPoToken?.playerRequestPoToken,
        ).getOrNull() ?: return StreamHealth.ClientHealth.ERROR

        val playabilityStatus = response.playabilityStatus
        if (playabilityStatus.status != "OK") {
            return if (isBotDetectionError(playabilityStatus.reason.orEmpty())) {
                StreamHealth.ClientHealth.BOT_GATED
            } else {
                StreamHealth.ClientHealth.UNAVAILABLE
            }
        }

        val candidates = selectAudioFormatCandidates(
            response,
            AudioQuality.HIGH,
            networkMetered = false,
            avoidCodecs = emptySet(),
        )
        val format = candidates.firstOrNull() ?: return StreamHealth.ClientHealth.UNAVAILABLE
        val url = findUrlOrNull(format, videoId, client, webPoToken) ?: return StreamHealth.ClientHealth.UNAVAILABLE

        return if (validateStatus(url, client.userAgent, format.contentLength) == null) {
            StreamHealth.ClientHealth.WORKING
        } else {
            // plays at first but the stream gets cut off partway through
            StreamHealth.ClientHealth.TRUNCATED
        }
    }
    //endregion
}
