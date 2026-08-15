package com.mindfullinking.app

import android.util.Log

/**
 * Logcat Tree / Utility that redacts URLs, titles, query parameters, and user notes from debug output.
 * Ensures zero PII (Personally Identifiable Information) or link data is leaked to logcat.
 */
object SanitizingLogTree {

    private val URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    private val QUERY_PARAM_REGEX = Regex("""\?[^\s]*""")
    private val SENSITIVE_KEY_VALUE = Regex("""(?i)(title|note|url|canonicalUrl|displayUrl)\s*[:=]\s*[^,\n]+""")

    fun sanitize(message: String): String {
        var sanitized = message
        sanitized = sanitized.replace(URL_REGEX, "[REDACTED_URL]")
        sanitized = sanitized.replace(QUERY_PARAM_REGEX, "?[REDACTED_PARAMS]")
        sanitized = sanitized.replace(SENSITIVE_KEY_VALUE) { match ->
            val key = match.groupValues[1]
            "$key=[REDACTED]"
        }
        return sanitized
    }

    fun d(tag: String, message: String) {
        Log.d(tag, sanitize(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, sanitize(message))
    }

    fun w(tag: String, message: String) {
        Log.w(tag, sanitize(message))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, sanitize(message), throwable)
    }
}
