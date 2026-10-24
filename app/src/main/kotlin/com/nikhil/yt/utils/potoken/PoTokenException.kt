/*
 * Velune / Openbeats — PO token WebView generator.
 * Based on OuterTune's poToken implementation (GPL-3.0), which is itself based on
 * NewPipe's BotGuard WebView integration.
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils.potoken

class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException(error)
    else
        PoTokenException(error)
}
