/*
 * Openbeats - Glassmorphism Design System
 * Shared glass surfaces, mesh gradients, and glass card components
 */

package com.nikhil.yt.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

// ── Glassmorphism Color Palette ──────────────────────────────────────────────

object GlassPalette {
    // Primary accent – electric cyan
    val Cyan = Color(0xFF00D4FF)
    val CyanDim = Color(0xFF00A5CC)

    // Secondary accent – soft violet
    val Violet = Color(0xFF8B5CF6)
    val VioletDim = Color(0xFF6D28D9)

    // Tertiary accent – warm pink
    val Pink = Color(0xFFF472B6)
    val PinkDim = Color(0xFFDB2777)

    // Surface colors
    val GlassSurfaceDark = Color(0x1AFFFFFF)       // 10% white
    val GlassSurfaceMedium = Color(0x26FFFFFF)      // 15% white
    val GlassSurfaceLight = Color(0x33FFFFFF)        // 20% white

    val GlassBorderLight = Color(0x33FFFFFF)         // 20% white
    val GlassBorderMedium = Color(0x1FFFFFFF)        // 12% white

    // Background base
    val BackgroundDeep = Color(0xFF0A0E1A)
    val BackgroundMid = Color(0xFF111827)
    val BackgroundSurface = Color(0xFF1A1F35)
}

// ── Mesh Gradient Background ─────────────────────────────────────────────────

fun Modifier.glassMeshBackground(
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    val color1 = GlassPalette.Cyan
    val color2 = GlassPalette.Violet
    val color3 = GlassPalette.Pink
    val color4 = GlassPalette.CyanDim
    val color5 = GlassPalette.VioletDim
    val baseColor = GlassPalette.BackgroundDeep

    this.drawWithCache {
        val width = this.size.width
        val height = this.size.height

        val brush1 = Brush.radialGradient(
            colors = listOf(
                color1.copy(alpha = 0.30f),
                color1.copy(alpha = 0.15f),
                color1.copy(alpha = 0.06f),
                Color.Transparent
            ),
            center = Offset(width * 0.12f, height * 0.08f),
            radius = width * 0.55f
        )

        val brush2 = Brush.radialGradient(
            colors = listOf(
                color2.copy(alpha = 0.28f),
                color2.copy(alpha = 0.14f),
                color2.copy(alpha = 0.05f),
                Color.Transparent
            ),
            center = Offset(width * 0.88f, height * 0.15f),
            radius = width * 0.60f
        )

        val brush3 = Brush.radialGradient(
            colors = listOf(
                color3.copy(alpha = 0.22f),
                color3.copy(alpha = 0.10f),
                color3.copy(alpha = 0.04f),
                Color.Transparent
            ),
            center = Offset(width * 0.25f, height * 0.42f),
            radius = width * 0.55f
        )

        val brush4 = Brush.radialGradient(
            colors = listOf(
                color4.copy(alpha = 0.18f),
                color4.copy(alpha = 0.08f),
                color4.copy(alpha = 0.03f),
                Color.Transparent
            ),
            center = Offset(width * 0.75f, height * 0.55f),
            radius = width * 0.65f
        )

        val brush5 = Brush.radialGradient(
            colors = listOf(
                color5.copy(alpha = 0.15f),
                color5.copy(alpha = 0.07f),
                color5.copy(alpha = 0.02f),
                Color.Transparent
            ),
            center = Offset(width * 0.50f, height * 0.80f),
            radius = width * 0.75f
        )

        val overlayBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                baseColor.copy(alpha = 0.30f),
                baseColor.copy(alpha = 0.65f),
                baseColor
            ),
            startY = height * 0.35f,
            endY = height
        )

        onDrawBehind {
            drawRect(brush = brush1)
            drawRect(brush = brush2)
            drawRect(brush = brush3)
            drawRect(brush = brush4)
            drawRect(brush = brush5)
            drawRect(brush = overlayBrush)
        }
    }
}

// ── Glass Card Composable ────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    surfaceColor: Color = GlassPalette.GlassSurfaceDark,
    borderColor: Color = GlassPalette.GlassBorderLight,
    borderAlpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceColor)
            .border(
                width = 0.8.dp,
                color = borderColor.copy(alpha = borderAlpha),
                shape = shape
            )
            .padding(16.dp),
        content = content
    )
}

// ── Glass Surface Modifier ───────────────────────────────────────────────────

fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    surfaceColor: Color = GlassPalette.GlassSurfaceDark,
    borderColor: Color = GlassPalette.GlassBorderLight,
): Modifier = this.then(
    Modifier
        .clip(shape)
        .background(surfaceColor)
        .border(width = 0.8.dp, color = borderColor, shape = shape)
)

// ── Glass Item Card (for list items like songs, albums) ──────────────────────

@Composable
fun GlassItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                surfaceColor = GlassPalette.GlassSurfaceDark,
                borderColor = GlassPalette.GlassBorderMedium,
            )
            .padding(12.dp),
        content = content
    )
}

// ── Glass Section Header ─────────────────────────────────────────────────────

@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

// ── Glass Chip ───────────────────────────────────────────────────────────────

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) GlassPalette.Cyan.copy(alpha = 0.25f)
                else GlassPalette.GlassSurfaceDark
            )
            .border(
                width = 0.8.dp,
                color = if (selected) GlassPalette.Cyan.copy(alpha = 0.5f)
                else GlassPalette.GlassBorderMedium,
                shape = shape
            )
            .then(
                if (onClick != {}) {
                    Modifier.then(Modifier) // clickable handled outside
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) GlassPalette.Cyan
            else Color.White.copy(alpha = 0.7f),
        )
    }
}
