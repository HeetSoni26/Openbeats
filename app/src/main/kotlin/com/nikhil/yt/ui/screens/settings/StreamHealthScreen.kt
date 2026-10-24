/*
 * Velune - by Nikhil
 * Playback diagnostics: shows which innertube clients can currently serve complete
 * streams, lets the user re-probe on demand, and lists recent stream resolution events.
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.constants.AutoPoTokenEnabledKey
import com.nikhil.yt.constants.StreamClientProbeAtKey
import com.nikhil.yt.constants.StreamClientProbeJsonKey
import com.nikhil.yt.ui.component.IconButton
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.ui.component.SwitchPreference
import com.nikhil.yt.ui.utils.backToMain
import com.nikhil.yt.utils.StreamHealth
import com.nikhil.yt.utils.YTPlayerUtils
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.rememberPreference
import com.nikhil.yt.utils.reportException
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamHealthScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var (autoPoTokenEnabled, onAutoPoTokenEnabledChange) = rememberPreference(
        AutoPoTokenEnabledKey,
        defaultValue = true
    )
    var (lastProbeAt, onLastProbeAtChange) = rememberPreference(
        StreamClientProbeAtKey,
        defaultValue = 0L
    )
    var probeResults by remember { mutableStateOf(StreamHealth.lastProbeResults) }
    var probing by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(StreamHealth.recentEvents()) }

    fun runProbe() {
        if (probing) return
        probing = true
        scope.launch {
            try {
                YTPlayerUtils.probeStreamClients()
                probeResults = StreamHealth.lastProbeResults
                events = StreamHealth.recentEvents()
                val now = System.currentTimeMillis()
                onLastProbeAtChange(now)
                context.dataStore.edit { settings ->
                    settings[StreamClientProbeJsonKey] = StreamHealth.ranksToJson()
                    settings[StreamClientProbeAtKey] = now
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                probing = false
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Text(
            text = stringResource(R.string.stream_health_desc),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.auto_po_token)) },
            description = stringResource(R.string.auto_po_token_desc),
            checked = autoPoTokenEnabled,
            onCheckedChange = onAutoPoTokenEnabledChange,
        )

        if (lastProbeAt > 0L) {
            Text(
                text = stringResource(R.string.last_probe) + " " +
                        DateFormat.getDateTimeInstance().format(Date(lastProbeAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Button(
            onClick = { runProbe() },
            enabled = !probing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            if (probing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.probing_clients))
            } else {
                Text(stringResource(R.string.test_all_clients))
            }
        }

        if (probeResults.isNotEmpty()) {
            PreferenceGroupTitle(title = stringResource(R.string.client_status))
            probeResults.forEach { entry ->
                ClientHealthRow(entry)
            }
        }

        PreferenceGroupTitle(title = stringResource(R.string.recent_events))
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.no_events_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            events.take(30).forEach { event ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = event.clientName,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = event.outcome,
                            style = MaterialTheme.typography.labelMedium,
                            color = eventColor(event.outcome),
                        )
                        Spacer(
                            Modifier
                                .weight(1f)
                        )
                        Text(
                            text = DateFormat.getTimeInstance().format(Date(event.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (event.detail.isNotBlank()) {
                        Text(
                            text = event.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.playback_diagnostics)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}

@Composable
private fun ClientHealthRow(entry: StreamHealth.ClientHealthEntry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = when (entry.health) {
                StreamHealth.ClientHealth.WORKING -> Color(0xFF22C55E)
                StreamHealth.ClientHealth.TRUNCATED -> Color(0xFFF59E0B)
                StreamHealth.ClientHealth.UNKNOWN -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.error
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.clientName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${entry.clientVersion} · ${entry.health.name.lowercase()} · ${entry.elapsedMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun eventColor(outcome: String): Color = when {
    outcome == "OK" || outcome == "PROBE_WORKING" -> Color(0xFF22C55E)
    outcome == "STALE_URL" || outcome == "PROBE_TRUNCATED" || outcome == "UNAVAILABLE" -> Color(0xFFF59E0B)
    else -> MaterialThemeError
}

private val MaterialThemeError = Color(0xFFEF4444)
