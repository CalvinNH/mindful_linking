package com.mindfullinking.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecommendationSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<RecommendationSessionEntity>)

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at_epoch_ms DESC, id DESC LIMIT 1")
    suspend fun getLatestSession(): RecommendationSessionEntity?

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at_epoch_ms DESC, id DESC LIMIT :limit")
    suspend fun getMostRecentSessions(limit: Int): List<RecommendationSessionEntity>

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at_epoch_ms DESC, id DESC LIMIT 2")
    fun getSessionsFlow(): Flow<List<RecommendationSessionEntity>>

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at_epoch_ms DESC, id DESC")
    suspend fun getAllSessions(): List<RecommendationSessionEntity>

    @Transaction
    suspend fun insertCompletedSession(session: RecommendationSessionEntity) {
        insertSession(session)
        deleteAllExceptMostRecent(limit = 2)
    }

    @Query("DELETE FROM recommendation_sessions WHERE started_at_epoch_ms < :beforeEpochMs")
    suspend fun deleteOldSessions(beforeEpochMs: Long)

    @Query(
        """
        DELETE FROM recommendation_sessions
        WHERE id NOT IN (
            SELECT id FROM recommendation_sessions
            ORDER BY started_at_epoch_ms DESC, id DESC
            LIMIT :limit
        )
        """
    )
    suspend fun deleteAllExceptMostRecent(limit: Int)

    @Query("DELETE FROM recommendation_sessions")
    suspend fun deleteAllSessions()
}
