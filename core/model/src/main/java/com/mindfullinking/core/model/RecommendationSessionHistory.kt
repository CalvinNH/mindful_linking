package com.mindfullinking.core.model

/**
 * The bounded recommendation history used by the deterministic auction engine.
 * It is updated only after an auction reaches completed results.
 */
data class RecommendationSessionHistory(
    val lastRecommendedLinkIds: List<String> = emptyList(),
    val previousRecommendedLinkIds: List<String> = emptyList()
) {
    fun afterCompletedAuction(recommendedLinkIds: List<String>): RecommendationSessionHistory =
        RecommendationSessionHistory(
            lastRecommendedLinkIds = recommendedLinkIds,
            previousRecommendedLinkIds = lastRecommendedLinkIds
        )

    companion object {
        fun fromSessions(sessions: List<RecommendationSession>): RecommendationSessionHistory =
            RecommendationSessionHistory(
                lastRecommendedLinkIds = sessions.getOrNull(0)?.recommendedLinkIds.orEmpty(),
                previousRecommendedLinkIds = sessions.getOrNull(1)?.recommendedLinkIds.orEmpty()
            )
    }
}
