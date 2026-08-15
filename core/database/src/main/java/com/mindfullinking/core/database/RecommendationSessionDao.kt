package com.mindfullinking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecommendationSessionEntity)

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at_epoch_ms DESC LIMIT 1")
    suspend fun getLatestSession(): RecommendationSessionEntity?

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at_epoch_ms DESC")
    fun getSessionsFlow(): Flow<List<RecommendationSessionEntity>>

    @Query("DELETE FROM recommendation_sessions WHERE started_at_epoch_ms < :beforeEpochMs")
    suspend fun deleteOldSessions(beforeEpochMs: Long)

    @Query("DELETE FROM recommendation_sessions")
    suspend fun deleteAllSessions()
}
