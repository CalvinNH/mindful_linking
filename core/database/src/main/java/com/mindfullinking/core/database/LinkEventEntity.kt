package com.mindfullinking.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "link_events")
data class LinkEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "link_id") val linkId: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "timestamp_epoch_ms") val timestampEpochMs: Long,
    @ColumnInfo(name = "details") val details: String?
)
