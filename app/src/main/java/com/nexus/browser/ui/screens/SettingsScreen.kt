package com.nexus.browser.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton
import com.nexus.browser.adblock.AdBlocker
import com.nexus.browser.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val darkTheme by settings.darkTheme.collectAsState(initial = false)
    val dynamicColor by settings.dynamicColor.collectAsState(initial = false)
    val adBlockEnabled by settings.adBlockEnabled.collectAsState(initial = true)
    val cursorEnabled by settings.cursorEnabled.collectAsState(initial = true)
    val filterStatus by AdBlocker.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        SettingRow(
            title = "Ad Blocking",
            subtitle = "EasyList + AdGuard filters • $filterStatus",
            checked = adBlockEnabled,
            onCheckedChange = { scope.launch { settings.setAdBlockEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingRow(
            title = "Dark Theme",
            subtitle = "Use the dark Material 3 Expressive theme",
            checked = darkTheme,
            onCheckedChange = { scope.launch { settings.setDarkTheme(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingRow(
            title = "Dynamic Color",
            subtitle = "Match wallpaper colors (Android 12+)",
            checked = dynamicColor,
            onCheckedChange = { scope.launch { settings.setDynamicColor(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingRow(
            title = "D-pad Cursor",
            subtitle = "Move a mouse-style cursor with the remote",
            checked = cursorEnabled,
            onCheckedChange = { scope.launch { settings.setCursorEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Nexus Browser 0.2.0-alpha\nOptimized for Android TV • ARMv7 + arm64\nTarget: Onn 4K Pro & Google TV",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
