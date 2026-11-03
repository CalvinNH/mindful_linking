package com.mindfullinking.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_sessions")
data class RecommendationSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "available_minutes") val availableMinutes: Int,
    @ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
    @ColumnInfo(name = "recommended_link_ids_json") val recommendedLinkIdsJson: String
)
