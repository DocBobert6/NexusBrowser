package com.nexus.browser.cursor

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp
import kotlin.math.hypot

/**
 * D-pad driven software cursor for Android TV.
 *
 * The stock remote has no pointer, so the D-pad becomes the mouse:
 * directions move the cursor, center (or gamepad A) clicks. Movement uses
 * velocity ramping (slow start, fast cruise) plus exponential smoothing on
 * the rendered position, updated every display frame — the smoothest motion
 * achievable without a physical pointer.
 *
 * Positions are normalized (0..1) so the cursor survives rotation and
 * resolution changes; the overlay converts to pixels when drawing.
 */
class CursorController {

    data class Pos(val x: Float, val y: Float)

    private val _target = MutableStateFlow(Pos(0.5f, 0.42f))
    private val _drawn = MutableStateFlow(Pos(0.5f, 0.42f))

    /** Smoothed, render-ready cursor position (normalized 0..1). */
    val drawn: StateFlow<Pos> = _drawn.asStateFlow()

    /** True while the cursor should be visible on screen. */
    val visible = MutableStateFlow(true)

    /** Real pixel dimensions of the drawing surface; set by the overlay. */
    var viewWidth: Float = 1920f
    var viewHeight: Float = 1080f

    /** Fired on click with the cursor position in real pixels. */
    var onClick: ((x: Float, y: Float) -> Unit)? = null

    private val pressed = mutableSetOf<Int>()
    private var holdTime = 0f

    // Speeds are fractions of screen dimension per second, so the feel is
    // identical at 1080p and 4K.
    private val startSpeed = 0.30f
    private val maxSpeed = 1.50f
    private val rampTime = 0.85f

    /**
     * Returns true if the key was consumed by the cursor.
     * Call from the activity's onKeyDown before super.
     */
    fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pressed.add(keyCode)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.repeatCount == 0) {
                    val p = _drawn.value
                    onClick?.invoke(p.x * viewWidth, p.y * viewHeight)
                }
                true
            }
            else -> false
        }
    }

    /**
     * Returns true if the key was consumed by the cursor.
     * Call from the activity's onKeyUp before super.
     */
    fun handleKeyUp(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pressed.remove(keyCode)
                if (pressed.isEmpty()) holdTime = 0f
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A -> true
            else -> false
        }
    }

    /**
     * Advance the simulation by dt seconds. Called once per display frame
     * from the overlay's frame loop.
     */
    fun update(dt: Float) {
        if (pressed.isNotEmpty()) {
            holdTime += dt
            val ramp = (holdTime / rampTime).coerceIn(0f, 1f)
            val speed = startSpeed + (maxSpeed - startSpeed) * ramp * ramp

            var dx = 0f
            var dy = 0f
            if (KeyEvent.KEYCODE_DPAD_LEFT in pressed) dx -= 1f
            if (KeyEvent.KEYCODE_DPAD_RIGHT in pressed) dx += 1f
            if (KeyEvent.KEYCODE_DPAD_UP in pressed) dy -= 1f
            if (KeyEvent.KEYCODE_DPAD_DOWN in pressed) dy += 1f
            val len = hypot(dx, dy)
            if (len > 0f) {
                dx /= len
                dy /= len
            }
            // Equal pixel speed on both axes (aspect-corrected) so
            // diagonals track at a true 45 degrees on 16:9 screens.
            val aspect = (viewWidth / viewHeight).coerceIn(0.5f, 4f)
            val t = _target.value
            _target.value = Pos(
                (t.x + dx * speed * dt).coerceIn(0f, 1f),
                (t.y + dy * speed * aspect * dt).coerceIn(0f, 1f)
            )
        } else {
            holdTime = 0f
        }

        // Exponential smoothing — the "butter". Frame-rate independent.
        val d = _drawn.value
        val t = _target.value
        val k = 1f - exp(-dt * 24f)
        _drawn.value = Pos(
            d.x + (t.x - d.x) * k,
            d.y + (t.y - d.y) * k
        )
    }
}
