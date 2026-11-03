package com.mindfullinking.core.database

import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.CreatedFrom
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEvent
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.MetadataFetchStatus
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.model.RecommendationSession
import com.mindfullinking.core.model.ResolutionReason
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate

fun LinkEntity.toDomainModel(): Link {
    return Link(
        id = id,
        canonicalUrl = canonicalUrl,
        displayUrl = displayUrl,
        title = title,
        note = note,
        priority = Priority.fromInt(priority),
        estimatedDurationMinutes = estimatedDurationMinutes,
        deadlineAt = deadlineAtEpochMs?.let { Instant.ofEpochMilli(it) },
        deadlineDateOnly = deadlineDateOnly?.let(LocalDate::parse),
        deadlineHasTime = deadlineHasTime,
        deadlineNotificationsEnabled = deadlineNotificationsEnabled,
        status = LinkStatus.fromString(status),
        resolutionReason = ResolutionReason.fromString(resolutionReason),
        savedAt = Instant.ofEpochMilli(savedAtEpochMs),
        resolvedAt = resolvedAtEpochMs?.let { Instant.ofEpochMilli(it) },
        lastOpenedAt = lastOpenedAtEpochMs?.let { Instant.ofEpochMilli(it) },
        snoozedUntil = snoozedUntilEpochMs?.let { Instant.ofEpochMilli(it) },
        skipCount = skipCount,
        lastSkippedAt = lastSkippedAtEpochMs?.let { Instant.ofEpochMilli(it) },
        recommendationCount = recommendationCount,
        lastRecommendedAt = lastRecommendedAtEpochMs?.let { Instant.ofEpochMilli(it) },
        createdFrom = CreatedFrom.fromString(createdFrom),
        metadataFetchStatus = MetadataFetchStatus.fromString(metadataFetchStatus),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs)
    )
}

fun Link.toEntity(): LinkEntity {
    return LinkEntity(
        id = id,
        canonicalUrl = canonicalUrl,
        displayUrl = displayUrl,
        title = title,
        note = note,
        priority = priority.value,
        estimatedDurationMinutes = estimatedDurationMinutes,
        deadlineAtEpochMs = deadlineAt?.toEpochMilli(),
        deadlineDateOnly = deadlineDateOnly?.toString(),
        deadlineHasTime = deadlineHasTime,
        deadlineNotificationsEnabled = deadlineNotificationsEnabled,
        status = status.name,
        resolutionReason = resolutionReason?.name,
        savedAtEpochMs = savedAt.toEpochMilli(),
        resolvedAtEpochMs = resolvedAt?.toEpochMilli(),
        lastOpenedAtEpochMs = lastOpenedAt?.toEpochMilli(),
        snoozedUntilEpochMs = snoozedUntil?.toEpochMilli(),
        skipCount = skipCount,
        lastSkippedAtEpochMs = lastSkippedAt?.toEpochMilli(),
        recommendationCount = recommendationCount,
        lastRecommendedAtEpochMs = lastRecommendedAt?.toEpochMilli(),
        createdFrom = createdFrom.name,
        metadataFetchStatus = metadataFetchStatus.name,
        updatedAtEpochMs = updatedAt.toEpochMilli()
    )
}

fun BudgetStateEntity.toDomainModel(): BudgetState {
    return BudgetState(
        standardBudgetCents = standardBudgetCents,
        temporaryOverageCents = temporaryOverageCents,
        temporaryOverageExpiresAt = temporaryOverageExpiresAtEpochMs?.let { Instant.ofEpochMilli(it) },
        lastOverageReminderAt = lastOverageReminderAtEpochMs?.let { Instant.ofEpochMilli(it) }
    )
}

fun BudgetState.toEntity(id: Int = 1): BudgetStateEntity {
    return BudgetStateEntity(
        id = id,
        standardBudgetCents = standardBudgetCents,
        temporaryOverageCents = temporaryOverageCents,
        temporaryOverageExpiresAtEpochMs = temporaryOverageExpiresAt?.toEpochMilli(),
        lastOverageReminderAtEpochMs = lastOverageReminderAt?.toEpochMilli()
    )
}

fun RecommendationSessionEntity.toDomainModel(): RecommendationSession {
    val linkIds = runCatching {
        val array = JSONArray(recommendedLinkIdsJson)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        list
    }.getOrDefault(emptyList())

    return RecommendationSession(
        id = id,
        availableMinutes = availableMinutes,
        startedAt = Instant.ofEpochMilli(startedAtEpochMs),
        recommendedLinkIds = linkIds
    )
}

fun RecommendationSession.toEntity(): RecommendationSessionEntity {
    val jsonArray = JSONArray()
    recommendedLinkIds.forEach { jsonArray.put(it) }
    return RecommendationSessionEntity(
        id = id,
        availableMinutes = availableMinutes,
        startedAtEpochMs = startedAt.toEpochMilli(),
        recommendedLinkIdsJson = jsonArray.toString()
    )
}

fun LinkEventEntity.toDomainModel(): LinkEvent {
    return LinkEvent(
        id = id,
        linkId = linkId,
        eventType = LinkEventType.fromString(eventType),
        timestamp = Instant.ofEpochMilli(timestampEpochMs),
        details = details
    )
}

fun LinkEvent.toEntity(): LinkEventEntity {
    return LinkEventEntity(
        id = id,
        linkId = linkId,
        eventType = eventType.name,
        timestampEpochMs = timestamp.toEpochMilli(),
        details = details
    )
}
