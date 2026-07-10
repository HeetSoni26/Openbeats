/*
 * Openbeats - Enhanced Glassmorphism Effect Defaults
 */

package com.nikhil.yt.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassSurfaceStyle(
    val surfaceTint: Color,
    val surfaceAlpha: Float,
    val overlayColor: Color,
    val overlayAlpha: Float,
    val blurRadius: Dp,
    val useVibrancy: Boolean,
    val useLens: Boolean,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val borderColor: Color,
    val borderAlpha: Float,
    val backgroundDimAlpha: Float,
    val backgroundDimColor: Color,
)

object GlassEffectDefaults {

    // ── Navigation Bar ───────────────────────────────────────────────────────

    val NavigationBarDark = GlassSurfaceStyle(
        surfaceTint = Color(0xFF0A0E1A),
        surfaceAlpha = 0.50f,
        overlayColor = Color(0xFF111827),
        overlayAlpha = 0.35f,
        blurRadius = 48.dp,
        useVibrancy = true,
        useLens = true,
        lensHeight = 22.dp,
        lensAmount = 44.dp,
        borderColor = Color.White,
        borderAlpha = 0.18f,
        backgroundDimAlpha = 0.40f,
        backgroundDimColor = Color(0xFF0A0E1A),
    )

    val NavigationBarLight = GlassSurfaceStyle(
        surfaceTint = Color.White,
        surfaceAlpha = 0.55f,
        overlayColor = Color.White,
        overlayAlpha = 0.45f,
        blurRadius = 56.dp,
        useVibrancy = true,
        useLens = true,
        lensHeight = 22.dp,
        lensAmount = 44.dp,
        borderColor = Color.White,
        borderAlpha = 0.25f,
        backgroundDimAlpha = 0.15f,
        backgroundDimColor = Color.Black,
    )

    val NavigationBarPureBlack = GlassSurfaceStyle(
        surfaceTint = Color(0xFF050508),
        surfaceAlpha = 0.72f,
        overlayColor = Color(0xFF0A0E1A),
        overlayAlpha = 0.50f,
        blurRadius = 44.dp,
        useVibrancy = true,
        useLens = true,
        lensHeight = 16.dp,
        lensAmount = 32.dp,
        borderColor = Color.White,
        borderAlpha = 0.08f,
        backgroundDimAlpha = 0.60f,
        backgroundDimColor = Color.Black,
    )

    // ── Mini Player ──────────────────────────────────────────────────────────

    val MiniPlayerDark = GlassSurfaceStyle(
        surfaceTint = Color(0xFF0A0E1A),
        surfaceAlpha = 0.45f,
        overlayColor = Color(0xFF111827),
        overlayAlpha = 0.32f,
        blurRadius = 44.dp,
        useVibrancy = true,
        useLens = true,
        lensHeight = 22.dp,
        lensAmount = 44.dp,
        borderColor = Color.White,
        borderAlpha = 0.20f,
        backgroundDimAlpha = 0.35f,
        backgroundDimColor = Color(0xFF0A0E1A),
    )

    val MiniPlayerLight = GlassSurfaceStyle(
        surfaceTint = Color.White,
        surfaceAlpha = 0.52f,
        overlayColor = Color.White,
        overlayAlpha = 0.42f,
        blurRadius = 52.dp,
        useVibrancy = true,
        useLens = true,
        lensHeight = 22.dp,
        lensAmount = 44.dp,
        borderColor = Color.White,
        borderAlpha = 0.22f,
        backgroundDimAlpha = 0.12f,
        backgroundDimColor = Color.Black,
    )

    val MiniPlayerPureBlack = GlassSurfaceStyle(
        surfaceTint = Color(0xFF050508),
        surfaceAlpha = 0.65f,
        overlayColor = Color(0xFF0A0E1A),
        overlayAlpha = 0.48f,
        blurRadius = 40.dp,
        useVibrancy = true,
        useLens = true,
        lensHeight = 18.dp,
        lensAmount = 36.dp,
        borderColor = Color.White,
        borderAlpha = 0.08f,
        backgroundDimAlpha = 0.55f,
        backgroundDimColor = Color.Black,
    )

    // ── Glass Card (for content cards) ───────────────────────────────────────

    val CardDark = GlassSurfaceStyle(
        surfaceTint = Color(0xFF0A0E1A),
        surfaceAlpha = 0.35f,
        overlayColor = Color(0xFF1A1F35),
        overlayAlpha = 0.25f,
        blurRadius = 36.dp,
        useVibrancy = true,
        useLens = false,
        lensHeight = 0.dp,
        lensAmount = 0.dp,
        borderColor = Color.White,
        borderAlpha = 0.14f,
        backgroundDimAlpha = 0.20f,
        backgroundDimColor = Color(0xFF0A0E1A),
    )

    val CardLight = GlassSurfaceStyle(
        surfaceTint = Color.White,
        surfaceAlpha = 0.45f,
        overlayColor = Color.White,
        overlayAlpha = 0.35f,
        blurRadius = 40.dp,
        useVibrancy = true,
        useLens = false,
        lensHeight = 0.dp,
        lensAmount = 0.dp,
        borderColor = Color.White,
        borderAlpha = 0.20f,
        backgroundDimAlpha = 0.10f,
        backgroundDimColor = Color.Black,
    )

    // ── Lookup helpers ───────────────────────────────────────────────────────

    fun navigationBarStyle(isDark: Boolean, isPureBlack: Boolean): GlassSurfaceStyle {
        return when {
            isPureBlack -> NavigationBarPureBlack
            isDark -> NavigationBarDark
            else -> NavigationBarLight
        }
    }

    fun miniPlayerStyle(isDark: Boolean, isPureBlack: Boolean): GlassSurfaceStyle {
        return when {
            isPureBlack -> MiniPlayerPureBlack
            isDark -> MiniPlayerDark
            else -> MiniPlayerLight
        }
    }

    fun cardStyle(isDark: Boolean): GlassSurfaceStyle {
        return if (isDark) CardDark else CardLight
    }
}
