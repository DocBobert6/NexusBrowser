package com.nexus.browser.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton
import com.nexus.browser.adblock.AdBlocker
import org.json.JSONArray

private val MEDIA_EXTENSIONS = listOf(".mp4", ".m3u8", ".webm", ".mov", ".m4v", ".mkv")

private fun isDirectMediaUrl(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return MEDIA_EXTENSIONS.any { path.endsWith(it) }
}

private fun hostOfUrl(url: String): String? {
    return try {
        val withoutScheme = url.substringAfter("://", "")
        if (withoutScheme.isEmpty()) return null
        withoutScheme.substringBefore('/').substringBefore('?').substringBefore(':')
            .lowercase().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String,
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("Loading…") }
    var pageHost by remember { mutableStateOf<String?>(null) }
    var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun scanForVideo(webView: WebView) {
        webView.evaluateJavascript(
            "(function(){var r=[];try{Array.prototype.forEach.call(" +
                    "document.querySelectorAll('video'),function(v){" +
                    "var s=v.currentSrc||v.src||'';" +
                    "if(!s){var so=v.querySelector('source');if(so){s=so.src||'';}}" +
                    "if(s){r.push(s);}});}catch(e){}return r;})()"
        ) { result ->
            try {
                if (result != null && result != "null") {
                    val urls = JSONArray(result)
                    if (urls.length() > 0) {
                        val first = urls.optString(0)
                        if (first.isNotEmpty() && first != detectedVideoUrl) {
                            detectedVideoUrl = first
                        }
                    }
                }
            } catch (_: Exception) {
                // Page JS unreadable — no handoff offered.
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar – remote friendly
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = {
                webViewRef?.loadUrl("https://www.google.com")
            }) {
                Icon(Icons.Default.Home, contentDescription = "Home")
            }
            IconButton(onClick = { webViewRef?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }

            Text(
                text = pageTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                maxLines = 1
            )

            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                                if (AdBlocker.isAd(url, pageHost)) {
                                    return AdBlocker.createEmptyResponse()
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                currentUrl = url ?: currentUrl
                                pageHost = url?.let { hostOfUrl(it) }
                                detectedVideoUrl = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                pageTitle = view?.title ?: url ?: "Nexus"
                                view?.let { wv ->
                                    scanForVideo(wv)
                                    // Videos often load after the page — rescan once.
                                    Handler(Looper.getMainLooper()).postDelayed(
                                        { scanForVideo(wv) },
                                        3000
                                    )
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                // Direct media files hand off to the native player.
                                if (isDirectMediaUrl(url)) {
                                    onPlayVideo(url)
                                    return true
                                }
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                // A page going fullscreen video hands off to our
                                // ExoPlayer instead, when we know the video URL.
                                val video = detectedVideoUrl
                                if (video != null) {
                                    callback?.onCustomViewHidden()
                                    onPlayVideo(video)
                                } else {
                                    super.onShowCustomView(view, callback)
                                }
                            }
                        }

                        loadUrl(initialUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView ->
                    webViewRef = webView
                }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // "Play video" pill — appears when a page video is detected.
            detectedVideoUrl?.let {
                Button(
                    onClick = { onPlayVideo(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Play video")
                }
            }
        }
    }
}
