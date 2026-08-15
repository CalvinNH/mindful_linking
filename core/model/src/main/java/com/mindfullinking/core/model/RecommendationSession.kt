package com.mindfullinking.core.model

import java.time.Instant

/**
 * Log entry of a completed recommendation session ("Use a link").
 */
data class RecommendationSession(
    val id: String,
    val availableMinutes: Int,
    val intentFilter: IntentCategory? = null,
    val startedAt: Instant = Instant.now(),
    val recommendedLinkIds: List<String> = emptyList()
)
