package com.mindfullinking.core.model

import java.time.Instant

/**
 * Lifecycle event logged for audit trailing and analytics redaction verification.
 */
data class LinkEvent(
    val id: String,
    val linkId: String,
    val eventType: LinkEventType,
    val timestamp: Instant = Instant.now(),
    val details: String? = null
)
