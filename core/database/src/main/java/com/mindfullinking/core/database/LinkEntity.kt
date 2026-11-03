package com.mindfullinking.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "links",
    indices = [
        Index(value = ["canonical_url"], unique = false),
        Index(value = ["status", "snoozed_until_epoch_ms"]),
        Index(value = ["status", "priority", "deadline_at_epoch_ms"])
    ]
)
data class LinkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "canonical_url") val canonicalUrl: String,
    @ColumnInfo(name = "display_url") val displayUrl: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "estimated_duration_minutes") val estimatedDurationMinutes: Int,
    @ColumnInfo(name = "deadline_at_epoch_ms") val deadlineAtEpochMs: Long?,
    @ColumnInfo(name = "deadline_date_only") val deadlineDateOnly: String?,
    @ColumnInfo(name = "deadline_has_time") val deadlineHasTime: Boolean,
    @ColumnInfo(name = "deadline_notifications_enabled") val deadlineNotificationsEnabled: Boolean,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "resolution_reason") val resolutionReason: String?,
    @ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMs: Long,
    @ColumnInfo(name = "resolved_at_epoch_ms") val resolvedAtEpochMs: Long?,
    @ColumnInfo(name = "last_opened_at_epoch_ms") val lastOpenedAtEpochMs: Long?,
    @ColumnInfo(name = "snoozed_until_epoch_ms") val snoozedUntilEpochMs: Long?,
    @ColumnInfo(name = "skip_count") val skipCount: Int,
    @ColumnInfo(name = "last_skipped_at_epoch_ms") val lastSkippedAtEpochMs: Long?,
    @ColumnInfo(name = "recommendation_count") val recommendationCount: Int,
    @ColumnInfo(name = "last_recommended_at_epoch_ms") val lastRecommendedAtEpochMs: Long?,
    @ColumnInfo(name = "created_from") val createdFrom: String,
    @ColumnInfo(name = "metadata_fetch_status") val metadataFetchStatus: String,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)
