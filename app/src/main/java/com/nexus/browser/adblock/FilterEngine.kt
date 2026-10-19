package com.nexus.browser.adblock

/**
 * Adblock Plus filter syntax engine (network rules).
 *
 * Supports the rule forms that do the heavy lifting in EasyList / AdGuard:
 *  - `||domain^` domain-anchored rules (the vast majority) via a fast
 *    suffix set — no regex needed
 *  - `@@` exception rules (checked before blocking rules)
 *  - `|` start / `|` end anchors, `*` wildcards, `^` separator placeholders
 *  - `$third-party`, `$~third-party`, `$domain=a|~b` options
 *  - `/regex/` rules
 *
 * Cosmetic rules (`##`, `#@#`, …) are intentionally skipped: this engine
 * blocks network requests, it does not restyle pages.
 *
 * Performance design (137k rules must answer in microseconds):
 *  - domain rules live in hash sets keyed by suffix walk of the host
 *  - every compiled regex carries a required literal "token" — the regex
 *    only runs when the URL contains the token (the uBlock Origin trick)
 *  - option-bearing domain rules are indexed by domain in a map
 *
 * Matching runs on WebView's background request thread; the engine is
 * immutable after loading (swap the whole instance to update).
 */
class FilterEngine {

    private data class PatternRule(
        val regex: Regex,
        val token: String?,
        val isException: Boolean
    )

    private data class OptionRule(
        val domain: String? = null,
        val pattern: Regex? = null,
        val token: String? = null,
        val substring: String? = null,
        val isException: Boolean = false,
        val thirdParty: Boolean? = null,
        val permitted: Set<String>? = null,
        val excluded: Set<String>? = null
    )

    private data class RuleOptions(
        val thirdParty: Boolean? = null,
        val permitted: Set<String>? = null,
        val excluded: Set<String>? = null,
        val cosmeticOnly: Boolean = false
    ) {
        fun isEmpty() = thirdParty == null && permitted == null && excluded == null
    }

    private val blockDomains = HashSet<String>()
    private val allowDomains = HashSet<String>()
    private val patternRules = ArrayList<PatternRule>()
    private val blockSubstrings = ArrayList<String>()
    private val allowSubstrings = ArrayList<String>()

    /** Option-bearing domain rules, indexed by their domain for O(1) lookup. */
    private val optionDomainRules = HashMap<String, MutableList<OptionRule>>()

    /** Option-bearing rules without a domain (pattern/substring based). */
    private val optionGenericRules = ArrayList<OptionRule>()

    /** Number of network rules loaded (for the settings UI). */
    var networkRuleCount: Int = 0
        private set

    fun addRules(lines: Sequence<String>) {
        for (line in lines) addRule(line.trim())
    }

    fun addRules(text: String) {
        addRules(text.lineSequence())
    }

    /**
     * @param url the requested resource URL
     * @param pageHost host of the page being viewed (for third-party /
     * $domain= evaluation); null if unknown
     */
    fun matches(url: String, pageHost: String?): Boolean {
        val lower = url.lowercase()
        val host = hostOf(lower) ?: return false
        val thirdParty = pageHost != null && !sameSite(host, pageHost.lowercase())

        // Exceptions first — an allow rule always wins.
        if (domainSuffixMatch(host, allowDomains)) return false
        if (matchOptionDomainRules(host, lower, pageHost, thirdParty, true)) return false
        if (matchOptionGenericRules(lower, pageHost, thirdParty, true)) return false
        if (allowSubstrings.any { lower.contains(it) }) return false
        if (matchPatternRules(lower, true)) return false

        if (domainSuffixMatch(host, blockDomains)) return true
        if (matchOptionDomainRules(host, lower, pageHost, thirdParty, false)) return true
        if (matchOptionGenericRules(lower, pageHost, thirdParty, false)) return true
        if (blockSubstrings.any { lower.contains(it) }) return true
        if (matchPatternRules(lower, false)) return true
        return false
    }

    private fun matchPatternRules(lowerUrl: String, isException: Boolean): Boolean {
        for (r in patternRules) {
            if (r.isException != isException) continue
            if (r.token != null && !lowerUrl.contains(r.token)) continue
            if (r.regex.containsMatchIn(lowerUrl)) return true
        }
        return false
    }

    private fun matchOptionDomainRules(
        host: String,
        lowerUrl: String,
        pageHost: String?,
        thirdParty: Boolean,
        isException: Boolean
    ): Boolean {
        var h: String? = host
        while (h != null) {
            val rules = optionDomainRules[h]
            if (rules != null) {
                for (r in rules) {
                    if (r.isException != isException) continue
                    if (!optionConstraintsMatch(r, pageHost, thirdParty)) continue
                    val targetOk = r.pattern == null ||
                            ((r.token == null || lowerUrl.contains(r.token)) &&
                                    r.pattern.containsMatchIn(lowerUrl))
                    if (targetOk) return true
                }
            }
            val dot = h.indexOf('.')
            h = if (dot < 0) null else h.substring(dot + 1)
        }
        return false
    }

    private fun matchOptionGenericRules(
        lowerUrl: String,
        pageHost: String?,
        thirdParty: Boolean,
        isException: Boolean
    ): Boolean {
        for (r in optionGenericRules) {
            if (r.isException != isException) continue
            if (!optionConstraintsMatch(r, pageHost, thirdParty)) continue
            val targetOk = when {
                r.pattern != null ->
                    (r.token == null || lowerUrl.contains(r.token)) &&
                            r.pattern.containsMatchIn(lowerUrl)
                r.substring != null -> lowerUrl.contains(r.substring)
                else -> false
            }
            if (targetOk) return true
        }
        return false
    }

    private fun optionConstraintsMatch(
        r: OptionRule,
        pageHost: String?,
        thirdParty: Boolean
    ): Boolean {
        r.thirdParty?.let { if (it != thirdParty) return false }
        val page = pageHost?.lowercase()
        if (page != null) {
            r.excluded?.let { ex -> if (ex.any { domainSuffixMatch(page, it) }) return false }
            r.permitted?.let { pm -> if (pm.none { domainSuffixMatch(page, it) }) return false }
        } else if (r.permitted != null) {
            return false
        }
        return true
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    private fun addRule(raw: String) {
        if (raw.isEmpty() || raw[0] == '!' || raw[0] == '[') return
        var line = raw
        var isException = false
        if (line.startsWith("@@")) {
            isException = true
            line = line.substring(2)
        }
        // Cosmetic / scriptlet / HTML-filtering rules: not network rules.
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") ||
            line.contains("#\$#") || line.contains("#%#")
        ) return

        // Split pattern from $options (regex rules /.../ may contain $).
        var pattern = line
        var options: String? = null
        if (!(pattern.startsWith("/") && pattern.endsWith("/") && pattern.length > 2)) {
            val dollar = pattern.indexOf('$')
            if (dollar >= 0) {
                options = pattern.substring(dollar + 1)
                pattern = pattern.substring(0, dollar)
            }
        }
        if (pattern.isEmpty()) return

        val opts = parseOptions(options)
        if (opts.cosmeticOnly) return

        // Domain-anchored rules: ||domain^...
        if (pattern.startsWith("||")) {
            val rest = pattern.substring(2)
            val cut = rest.indexOfFirst { it == '/' || it == '^' || it == '*' }
            val domain = (if (cut < 0) rest else rest.substring(0, cut)).lowercase()
            if (domain.isEmpty()) return
            val remainder = if (cut < 0) "" else rest.substring(cut)
            networkRuleCount++
            if ((remainder.isEmpty() || remainder == "^") && opts.isEmpty()) {
                (if (isException) allowDomains else blockDomains).add(domain)
            } else {
                val regex = patternToRegex(pattern)
                val token = extractToken(pattern)
                val rule = OptionRule(
                    domain = domain, pattern = regex, token = token,
                    isException = isException, thirdParty = opts.thirdParty,
                    permitted = opts.permitted, excluded = opts.excluded
                )
                if (opts.isEmpty()) {
                    patternRules.add(PatternRule(regex, token, isException))
                } else {
                    optionDomainRules.getOrPut(domain) { mutableListOf() }.add(rule)
                }
            }
            return
        }

        // Raw regex rules: /.../
        if (pattern.length > 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
            try {
                val regex = Regex(pattern.substring(1, pattern.length - 1), RegexOption.IGNORE_CASE)
                networkRuleCount++
                if (opts.isEmpty()) {
                    patternRules.add(PatternRule(regex, extractToken(pattern), isException))
                } else {
                    optionGenericRules.add(
                        OptionRule(
                            pattern = regex, token = extractToken(pattern),
                            isException = isException, thirdParty = opts.thirdParty,
                            permitted = opts.permitted, excluded = opts.excluded
                        )
                    )
                }
            } catch (_: Exception) {
                // Invalid regex in the list — skip it.
            }
            return
        }

        val hasWildcards = pattern.contains('*') || pattern.contains('^') ||
                pattern.startsWith("|") || pattern.endsWith("|")
        networkRuleCount++
        if (!hasWildcards) {
            val sub = pattern.lowercase()
            if (opts.isEmpty()) {
                (if (isException) allowSubstrings else blockSubstrings).add(sub)
            } else {
                optionGenericRules.add(
                    OptionRule(
                        substring = sub, isException = isException,
                        thirdParty = opts.thirdParty, permitted = opts.permitted,
                        excluded = opts.excluded
                    )
                )
            }
        } else {
            val regex = patternToRegex(pattern)
            val token = extractToken(pattern)
            if (opts.isEmpty()) {
                patternRules.add(PatternRule(regex, token, isException))
            } else {
                optionGenericRules.add(
                    OptionRule(
                        pattern = regex, token = token, isException = isException,
                        thirdParty = opts.thirdParty, permitted = opts.permitted,
                        excluded = opts.excluded
                    )
                )
            }
        }
    }

    private fun parseOptions(options: String?): RuleOptions {
        if (options.isNullOrEmpty()) return RuleOptions()
        var thirdParty: Boolean? = null
        var permitted: MutableSet<String>? = null
        var excluded: MutableSet<String>? = null
        var cosmeticOnly = false
        for (rawOpt in options.split(',')) {
            val o = rawOpt.trim().lowercase()
            when {
                o == "third-party" || o == "3p" -> thirdParty = true
                o == "~third-party" || o == "first-party" || o == "1p" -> thirdParty = false
                o.startsWith("domain=") -> {
                    for (d in o.substring(7).split('|')) {
                        val dom = d.trim().lowercase()
                        if (dom.isEmpty()) continue
                        if (dom.startsWith("~")) {
                            if (excluded == null) excluded = mutableSetOf()
                            excluded.add(dom.substring(1))
                        } else {
                            if (permitted == null) permitted = mutableSetOf()
                            permitted.add(dom)
                        }
                    }
                }
                o == "elemhide" || o == "generichide" -> cosmeticOnly = true
            }
        }
        return RuleOptions(thirdParty, permitted, excluded, cosmeticOnly)
    }

    /**
     * Longest literal run in the pattern — any regex match must contain it,
     * so it works as a cheap pre-filter before running the regex.
     */
    private fun extractToken(pattern: String): String? {
        var best = ""
        val cur = StringBuilder()
        fun flush() {
            if (cur.length > best.length) best = cur.toString()
            cur.clear()
        }
        for (c in pattern) {
            if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') cur.append(c)
            else flush()
        }
        flush()
        return best.lowercase().takeIf { it.length >= 5 }
    }

    // ------------------------------------------------------------------
    // Matching helpers
    // ------------------------------------------------------------------

    private fun domainSuffixMatch(host: String, domains: Set<String>): Boolean {
        // Walk the host's own suffixes: O(labels) hash lookups.
        var h: String? = host
        while (h != null) {
            if (domains.contains(h)) return true
            val dot = h.indexOf('.')
            h = if (dot < 0) null else h.substring(dot + 1)
        }
        return false
    }

    private fun domainSuffixMatch(host: String, domain: String): Boolean {
        return host == domain || host.endsWith(".$domain")
    }

    private fun hostOf(url: String): String? {
        return try {
            var u = url
            val schemeEnd = u.indexOf("://")
            if (schemeEnd < 0) return null
            u = u.substring(schemeEnd + 3)
            val end = u.indexOfFirst { it == '/' || it == '?' || it == '#' || it == ':' }
            val host = (if (end < 0) u else u.substring(0, end)).lowercase()
            host.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun sameSite(a: String, b: String): Boolean {
        return baseDomain(a) == baseDomain(b)
    }

    private fun baseDomain(host: String): String {
        val parts = host.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    /**
     * Convert ABP pattern syntax to a Regex.
     * `*` -> .*, `^` -> separator-or-end, `|` anchors.
     */
    private fun patternToRegex(pattern: String): Regex {
        val sb = StringBuilder()
        var s = pattern
        if (s.startsWith("||")) {
            s = s.substring(2)
            var j = 0
            while (j < s.length && s[j] != '/' && s[j] != '^' && s[j] != '*') j++
            sb.append("^[a-z][a-z0-9+.-]*://([^/:?#]*\\.)?")
            sb.append(Regex.escape(s.substring(0, j)))
            s = s.substring(j)
            appendPatternBody(sb, s)
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }
        if (s.startsWith("|")) {
            sb.append("^")
            s = s.substring(1)
        }
        var endAnchor = false
        if (s.endsWith("|") && s.length > 1) {
            endAnchor = true
            s = s.substring(0, s.length - 1)
        }
        appendPatternBody(sb, s)
        if (endAnchor) sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    private fun appendPatternBody(sb: StringBuilder, s: String) {
        for (c in s) {
            when (c) {
                '*' -> sb.append(".*")
                '^' -> sb.append("(?:[^a-zA-Z0-9_.%\\-]|$)")
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
    }
}
