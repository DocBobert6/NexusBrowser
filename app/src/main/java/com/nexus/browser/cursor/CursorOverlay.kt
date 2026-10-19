package com.nexus.browser.cursor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

/**
 * Full-screen transparent overlay that renders the D-pad cursor.
 *
 * The cursor is a vector-drawn arrow in the modern macOS style — dark fill,
 * light outline — so it stays razor sharp at any resolution including 4K.
 * It deliberately consumes no input itself; clicks are synthesized by the
 * controller and dispatched to the underlying views.
 */
@Composable
fun CursorOverlay(
    controller: CursorController,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val drawn by controller.drawn.collectAsState()
    val visible by controller.visible.collectAsState()

    LaunchedEffect(configuration) {
        val d = density.density
        controller.viewWidth = configuration.screenWidthDp * d
        controller.viewHeight = configuration.screenHeightDp * d
    }

    // Per-frame simulation loop — the smoothest motion available on the
    // device, locked to the display's vsync.
    LaunchedEffect(controller) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f
                else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                controller.update(dt)
            }
        }
    }

    if (!visible) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val x = drawn.x * size.width
        val y = drawn.y * size.height

        // Cursor height ~4.5% of the smaller screen dimension: readable
        // from the couch, not clownish.
        val cursorH = minOf(size.width, size.height) * 0.045f
        val s = cursorH / 46f
        val outline = (cursorH * 0.09f).coerceAtLeast(2f)

        fun arrowPath(ox: Float, oy: Float): Path {
            // Sleek arrow polygon, hotspot (tip) at (0,0).
            return Path().apply {
                moveTo(ox + 0f * s, oy + 0f * s)
                lineTo(ox + 0f * s, oy + 42f * s)
                lineTo(ox + 12f * s, oy + 32f * s)
                lineTo(ox + 17f * s, oy + 45f * s)
                lineTo(ox + 23f * s, oy + 42f * s)
                lineTo(ox + 18f * s, oy + 29f * s)
                lineTo(ox + 30f * s, oy + 29f * s)
                close()
            }
        }

        // Soft drop shadow for visibility over light content.
        drawPath(
            path = arrowPath(x + cursorH * 0.045f, y + cursorH * 0.06f),
            color = Color.Black.copy(alpha = 0.35f)
        )
        // Light outline, then dark fill — the modern macOS look.
        drawPath(
            path = arrowPath(x, y),
            color = Color.White,
            style = Stroke(width = outline * 2f)
        )
        drawPath(
            path = arrowPath(x, y),
            color = Color(0xFF1A1A1E)
        )
    }
}
