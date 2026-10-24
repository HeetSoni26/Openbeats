/*
 * Velune / Openbeats — PO token WebView generator.
 * Based on OuterTune's poToken implementation (GPL-3.0), which is itself based on
 * NewPipe's BotGuard WebView integration.
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils.potoken

class WebPoTokenResult(
    // token bound to the video id, to be sent as serviceIntegrityDimensions.poToken
    // on player requests made by WEB-family clients
    val playerRequestPoToken: String,
    // token bound to the session id, to be appended as `pot` on gvs stream urls
    val streamingDataPoToken: String,
)
