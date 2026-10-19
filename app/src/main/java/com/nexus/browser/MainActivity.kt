package com.nexus.browser

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.nexus.browser.adblock.AdBlocker
import com.nexus.browser.cursor.CursorController
import com.nexus.browser.cursor.CursorOverlay
import com.nexus.browser.data.SettingsRepository
import com.nexus.browser.ui.screens.NexusApp
import com.nexus.browser.ui.theme.NexusBrowserTheme

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsRepository
    private val cursorController = CursorController()

    @Volatile
    private var cursorEnabled = true

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)
        AdBlocker.initialize(this)
        enableEdgeToEdge()

        cursorController.onClick = { x, y -> synthesizeTap(x, y) }

        setContent {
            val darkTheme by settings.darkTheme.collectAsState(initial = false)
            val dynamicColor by settings.dynamicColor.collectAsState(initial = false)
            val adBlockOn by settings.adBlockEnabled.collectAsState(initial = true)
            val cursorOn by settings.cursorEnabled.collectAsState(initial = true)
            cursorEnabled = cursorOn

            LaunchedEffect(adBlockOn) { AdBlocker.enabled = adBlockOn }

            NexusBrowserTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NexusApp(settings = settings)
                        if (cursorOn) {
                            CursorOverlay(controller = cursorController)
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (cursorEnabled && cursorController.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (cursorEnabled && cursorController.handleKeyUp(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Turns a D-pad center press into a real tap at the cursor position by
     * dispatching synthetic touch events to the view hierarchy — links,
     * buttons and WebView content all respond exactly as if touched.
     */
    private fun synthesizeTap(x: Float, y: Float) {
        val root = window.decorView
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 90, MotionEvent.ACTION_UP, x, y, 0)
        root.dispatchTouchEvent(down)
        root.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }
}
