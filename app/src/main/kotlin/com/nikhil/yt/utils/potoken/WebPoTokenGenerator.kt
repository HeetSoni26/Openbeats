/*
 * Velune / Openbeats — PO token WebView generator.
 * Based on OuterTune's poToken implementation (GPL-3.0), which is itself based on
 * NewPipe's BotGuard WebView integration.
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils.potoken

import android.util.Log
import android.webkit.CookieManager
import com.nikhil.yt.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Generates real BotGuard PO tokens through an off-screen WebView, bound to the current
 * session (visitor/data sync id) and per-video ids. These are the same tokens YouTube's
 * web player produces; sending them on WEB-family player requests and gvs stream urls
 * prevents YouTube from truncating or blocking streams mid-playback.
 */
object WebPoTokenGenerator {
    private val TAG = "WebPoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    /**
     * Returns real PO tokens for the given video, or null when generation is impossible
     * (broken WebView, no network, challenge failure). Never throws for expected failures.
     */
    fun getWebClientPoToken(videoId: String, sessionId: String): WebPoTokenResult? {
        if (!webViewSupported || webViewBadImpl || sessionId.isBlank()) {
            return null
        }

        return try {
            runBlocking { getWebClientPoTokenInternal(videoId, sessionId, forceRecreate = false) }
        } catch (e: Exception) {
            when (e) {
                is BadWebViewException -> {
                    Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    null
                }

                else -> {
                    // challenge/network failures are expected sometimes; the caller falls
                    // back to the token-less path
                    Log.w(TAG, "poToken generation failed: ${e.message}")
                    null
                }
            }
        }
    }

    /**
     * Manually drops the current BotGuard session so the next token mint runs
     * on a brand-new WebView. Also clears the "broken WebView" flag, giving a
     * device whose WebView was updated a second chance. Never throws.
     */
    fun resetSession() {
        if (!webViewSupported) return
        try {
            runBlocking {
                webPoTokenGenLock.withLock {
                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }
                    webPoTokenGenerator = null
                    webPoTokenSessionId = null
                    webPoTokenStreamingPot = null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "poToken session reset failed: ${e.message}")
        } finally {
            webViewBadImpl = false
        }
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun getWebClientPoTokenInternal(videoId: String, sessionId: String, forceRecreate: Boolean): WebPoTokenResult {
        Log.d(TAG, "Web poToken requested: $videoId, $sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired || webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    webPoTokenSessionId = sessionId

                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }

                    // create a new webPoTokenGenerator
                    webPoTokenGenerator = PoTokenWebView.getNewPoTokenGenerator(App.instance)

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }

        val playerPot = try {
            // Not using synchronized here, since poTokenGenerator would be able to generate
            // multiple poTokens in parallel if needed. The only important thing is for exactly one
            // streaming poToken (based on [sessionId]) to be generated before anything else.
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch;
                // this might happen for example if the app goes in the background and the WebView
                // content is lost
                Log.w(TAG, "Failed to obtain poToken, retrying", throwable)
                return getWebClientPoTokenInternal(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        Log.d(TAG, "[$videoId] playerPot=$playerPot, streamingPot=$streamingPot")

        return WebPoTokenResult(playerPot, streamingPot)
    }
}
