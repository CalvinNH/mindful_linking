package com.mindfullinking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: LinkEventEntity)

    @Query("SELECT * FROM link_events WHERE link_id = :linkId ORDER BY timestamp_epoch_ms DESC")
    fun getEventsForLinkFlow(linkId: String): Flow<List<LinkEventEntity>>

    @Query("SELECT * FROM link_events ORDER BY timestamp_epoch_ms DESC")
    fun getAllEventsFlow(): Flow<List<LinkEventEntity>>

    @Query("SELECT * FROM link_events")
    suspend fun getAllEvents(): List<LinkEventEntity>

    @Query("DELETE FROM link_events")
    suspend fun deleteAllEvents()
}
