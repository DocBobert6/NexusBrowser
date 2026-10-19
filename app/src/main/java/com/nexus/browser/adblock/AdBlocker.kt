package com.nexus.browser.adblock

import android.content.Context
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real filter-list ad blocking for WebView.
 *
 * Loads EasyList + AdGuard Base (full Adblock Plus syntax via [FilterEngine]),
 * with a bundled snapshot for instant protection on first launch and a
 * weekly background refresh. Swap-safe: the engine instance is replaced
 * atomically, so request interception never blocks on updates.
 */
object AdBlocker {

    private const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
    private const val ADGUARD_URL = "https://filters.adtidy.org/extension/chromium/filters/2.txt"
    private const val UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private const val PREFS = "nexus_filters"
    private const val KEY_UPDATED_AT = "filters_updated_at"

    /** Master switch, driven by Settings. */
    @Volatile
    var enabled = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow("Loading filters…")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile
    private var engine: FilterEngine? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        scope.launch {
            // 1. Bundled snapshot — protection from the first page load.
            val bundled = FilterEngine()
            try {
                appContext.assets.open("filters/fallback.txt").bufferedReader().useLines { lines ->
                    bundled.addRules(lines)
                }
            } catch (_: Exception) {
                // No snapshot bundled; full lists still load below.
            }
            if (bundled.networkRuleCount > 0) {
                engine = bundled
                _status.value = formatStatus(bundled.networkRuleCount, null)
            }

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cacheFile = File(File(appContext.filesDir, "filters").apply { mkdirs() }, "combined.txt")

            // 2. Cached full lists from a previous run.
            if (cacheFile.exists() && cacheFile.length() > 100_000) {
                try {
                    val cached = FilterEngine()
                    cacheFile.bufferedReader().useLines { cached.addRules(it) }
                    if (cached.networkRuleCount > 5_000) {
                        engine = cached
                        _status.value = formatStatus(
                            cached.networkRuleCount,
                            prefs.getLong(KEY_UPDATED_AT, 0)
                        )
                    }
                } catch (_: Exception) {
                    // Corrupt cache — fall through to download.
                }
            }

            // 3. Weekly refresh from the network.
            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_UPDATED_AT, 0) > UPDATE_INTERVAL_MS) {
                refreshLists(appContext, prefs, cacheFile, now)
            }
        }
    }

    /**
     * @param url the requested resource URL
     * @param pageHost host of the page being viewed (for third-party
     * evaluation); null if unknown
     */
    fun isAd(url: String, pageHost: String?): Boolean {
        if (!enabled) return false
        return engine?.matches(url, pageHost) ?: false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun refreshLists(
        appContext: Context,
        prefs: android.content.SharedPreferences,
        cacheFile: File,
        now: Long
    ) {
        _status.value = "Updating filter lists…"
        val combined = StringBuilder()
        var ok = false
        val easy = downloadText(EASYLIST_URL)
        if (easy != null && easy.length > 500_000) {
            combined.append(easy).append('\n')
            ok = true
        }
        val adguard = downloadText(ADGUARD_URL)
        if (adguard != null && adguard.length > 500_000) {
            combined.append(adguard).append('\n')
            ok = true
        }
        if (ok) {
            try {
                val full = FilterEngine()
                full.addRules(combined.toString().lineSequence())
                if (full.networkRuleCount > 5_000) {
                    engine = full
                    cacheFile.writeText(combined.toString())
                    prefs.edit().putLong(KEY_UPDATED_AT, now).apply()
                    _status.value = formatStatus(full.networkRuleCount, now)
                    return
                }
            } catch (_: Exception) {
                // Keep whatever engine is already live.
            }
        }
        // Refresh failed — keep current engine, report what we have.
        _status.value = formatStatus(engine?.networkRuleCount ?: 0, prefs.getLong(KEY_UPDATED_AT, 0))
    }

    private fun downloadText(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "NexusBrowser/0.2")
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatStatus(rules: Int, updatedAt: Long?): String {
        val count = "%,d rules".format(Locale.US, rules)
        if (updatedAt == null || updatedAt <= 0) return "$count (built-in)"
        val date = SimpleDateFormat("MMM d", Locale.US).format(Date(updatedAt))
        return "$count • updated $date"
    }
}
