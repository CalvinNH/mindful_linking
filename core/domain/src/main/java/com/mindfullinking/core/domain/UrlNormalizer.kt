package com.mindfullinking.core.domain

import java.net.IDN
import java.net.URLDecoder
import java.util.Locale

/**
 * Complete URL validation and normalization engine.
 * Implements PRD Section 7.2 URL validation and canonical normalization.
 *
 * Rules:
 * 1. Only HTTP and HTTPS schemes supported (case-insensitive check, lowercased in output).
 * 2. Reject URLs containing user credentials (e.g. https://user:pass@example.com).
 * 3. Lowercase scheme and host. Convert internationalized hostnames (IDN) to ASCII/Punycode.
 * 4. Remove default ports: port 80 for HTTP, port 443 for HTTPS.
 * 5. Strip exact tracking parameter allowlist (case-insensitive key comparison):
 *    utm_source, utm_medium, utm_campaign, utm_term, utm_content, utm_id,
 *    utm_source_platform, utm_creative_format, utm_marketing_tactic,
 *    gclid, dclid, gbraid, wbraid, msclkid, fbclid, igshid, _ga, _gl.
 *    Any param key starting with "utm_" is also stripped.
 * 6. Preserve non-allowlisted query flags (?ref=, ?v=, ?x=1).
 * 7. Preserve path casing, path segments, and fragments (#section).
 */
object UrlNormalizer {

    val TRACKING_PARAMS: Set<String> = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_source_platform", "utm_creative_format", "utm_marketing_tactic",
        "gclid", "dclid", "gbraid", "wbraid", "msclkid", "fbclid", "igshid", "_ga", "_gl"
    )

    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "URL cannot be blank" }

        // Find scheme separator
        val schemeIndex = trimmed.indexOf("://")
        require(schemeIndex != -1) { "Only HTTP and HTTPS schemes supported" }

        val scheme = trimmed.substring(0, schemeIndex).lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "Only HTTP and HTTPS schemes supported" }

        val rest = trimmed.substring(schemeIndex + 3)
        require(rest.isNotBlank()) { "Missing host" }

        // Separate fragment
        val hashIndex = rest.indexOf("#")
        val (beforeHash, fragment) = if (hashIndex != -1) {
            rest.substring(0, hashIndex) to rest.substring(hashIndex + 1)
        } else {
            rest to null
        }

        // Separate query
        val questionIndex = beforeHash.indexOf("?")
        val (beforeQuery, query) = if (questionIndex != -1) {
            beforeHash.substring(0, questionIndex) to beforeHash.substring(questionIndex + 1)
        } else {
            beforeHash to null
        }

        // Separate path
        val slashIndex = beforeQuery.indexOf("/")
        val (authority, path) = if (slashIndex != -1) {
            beforeQuery.substring(0, slashIndex) to beforeQuery.substring(slashIndex)
        } else {
            beforeQuery to ""
        }

        // Check embedded user info
        require(!authority.contains("@")) { "Embedded user credentials forbidden" }

        // Parse host and port
        val (rawHost, port) = if (authority.startsWith("[")) {
            // IPv6 address support bracket format [::1]:80
            val closingBracket = authority.indexOf("]")
            require(closingBracket != -1) { "Invalid IPv6 host format" }
            val hostPart = authority.substring(0, closingBracket + 1)
            val remainder = authority.substring(closingBracket + 1)
            val portPart = if (remainder.startsWith(":")) remainder.substring(1).toIntOrNull() ?: -1 else -1
            hostPart to portPart
        } else if (authority.contains(":")) {
            val colonIndex = authority.lastIndexOf(":")
            val hostPart = authority.substring(0, colonIndex)
            val portPart = authority.substring(colonIndex + 1).toIntOrNull() ?: -1
            hostPart to portPart
        } else {
            authority to -1
        }

        require(rawHost.isNotBlank()) { "Missing host" }

        val asciiHost = IDN.toASCII(rawHost.lowercase(Locale.ROOT))

        val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        val portSuffix = if (port != -1 && !isDefaultPort) ":$port" else ""

        // Process query parameters
        val normalizedQuery = if (query.isNullOrEmpty()) {
            ""
        } else {
            val pairs = query.split("&")
            val filteredPairs = pairs.filter { pair ->
                if (pair.isEmpty()) return@filter false
                val key = pair.split("=")[0]
                val decodedKey = try {
                    URLDecoder.decode(key, "UTF-8").lowercase(Locale.ROOT)
                } catch (_: Exception) {
                    key.lowercase(Locale.ROOT)
                }
                !TRACKING_PARAMS.contains(decodedKey) && !decodedKey.startsWith("utm_")
            }
            if (filteredPairs.isEmpty()) "" else "?" + filteredPairs.joinToString("&")
        }

        val fragmentSuffix = if (fragment != null) "#$fragment" else ""

        return "$scheme://$asciiHost$portSuffix$path$normalizedQuery$fragmentSuffix"
    }
}
