/*
 * Velune - by Nikhil
 * Central store for playback stream health: per-client probe results used to
 * dynamically reorder the fallback chain, and a ring buffer of recent
 * resolution events shown on the Playback diagnostics screen.
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

object StreamHealth {
    private const val TAG = "StreamHealth"
    private const val MAX_EVENTS = 100

    enum class ClientHealth { UNKNOWN, WORKING, TRUNCATED, BOT_GATED, UNAVAILABLE, ERROR }

    data class StreamEvent(
        val timestamp: Long,
        val videoId: String,
        val clientName: String,
        val outcome: String,
        val detail: String,
    )

    /** Whether real BotGuard PO tokens are generated automatically (fail-open). */
    @Volatile
    var autoPoTokenEnabled: Boolean = true

    /** clientName (uppercase) -> rank; lower sorts earlier in the fallback chain. */
    @Volatile
    var clientRanks: Map<String, Int> = emptyMap()

    private val events = ArrayDeque<StreamEvent>()

    fun record(videoId: String, clientName: String, outcome: String, detail: String = "") {
        val event = StreamEvent(System.currentTimeMillis(), videoId, clientName, outcome, detail)
        synchronized(events) {
            events.addFirst(event)
            while (events.size > MAX_EVENTS) events.removeLast()
        }
        if (outcome != "OK") {
            Log.w(TAG, "$clientName: $outcome ${detail.take(120)}")
        }
    }

    fun recentEvents(): List<StreamEvent> = synchronized(events) { events.toList() }

    /** Rank used to sort fallback clients: working clients first, unknown next. */
    fun rankFor(clientName: String): Int =
        clientRanks[clientName.trim().uppercase()] ?: 1

    data class ClientHealthEntry(
        val clientName: String,
        val clientVersion: String,
        val health: ClientHealth,
        val elapsedMs: Long,
    )

    /** Latest probe results, for the Playback diagnostics screen. */
    @Volatile
    var lastProbeResults: List<ClientHealthEntry> = emptyList()
        private set

    fun updateRanksFromEntries(entries: List<ClientHealthEntry>) {
        lastProbeResults = entries
        updateRanks(entries.map { it.clientName to it.health })
    }

    private fun updateRanks(results: List<Pair<String, ClientHealth>>) {
        clientRanks = results.associate { (name, health) ->
            name.trim().uppercase() to when (health) {
                ClientHealth.WORKING -> 0
                ClientHealth.UNKNOWN -> 1
                else -> 2
            }
        }
    }

    fun ranksToJson(): String {
        val arr = JSONArray()
        lastProbeResults.forEach { entry ->
            arr.put(
                JSONObject()
                    .put("client", entry.clientName)
                    .put("version", entry.clientVersion)
                    .put("health", entry.health.name)
                    .put("elapsedMs", entry.elapsedMs)
            )
        }
        return arr.toString()
    }

    fun ranksFromJson(json: String?) {
        if (json.isNullOrBlank()) return
        runCatching {
            val arr = JSONArray(json)
            val entries = mutableListOf<ClientHealthEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val health = runCatching { ClientHealth.valueOf(obj.getString("health")) }
                    .getOrDefault(ClientHealth.UNKNOWN)
                entries.add(
                    ClientHealthEntry(
                        obj.getString("client"),
                        obj.optString("version", ""),
                        health,
                        obj.optLong("elapsedMs", 0L),
                    )
                )
            }
            updateRanksFromEntries(entries)
        }.onFailure { Log.w(TAG, "Failed to parse stored probe results", it) }
    }
}
