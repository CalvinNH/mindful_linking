package com.mindfullinking.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction

@Database(
    entities = [
        LinkEntity::class,
        BudgetStateEntity::class,
        RecommendationSessionEntity::class,
        LinkEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LinkLedgerDatabase : RoomDatabase() {

    abstract fun linkDao(): LinkDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recommendationSessionDao(): RecommendationSessionDao
    abstract fun linkEventDao(): LinkEventDao

    /** Deletes all user records together so a wipe cannot leave a partial ledger. */
    suspend fun clearAllUserData() = withTransaction {
        linkDao().deleteAllLinks()
        budgetDao().resetBudgetState()
        linkEventDao().deleteAllEvents()
        recommendationSessionDao().deleteAllSessions()
    }

    /** Replaces all user records only after a caller has fully validated a backup. */
    suspend fun replaceAllUserData(
        links: List<LinkEntity>,
        budgetState: BudgetStateEntity?,
        events: List<LinkEventEntity>,
        sessions: List<RecommendationSessionEntity>
    ) = withTransaction {
        clearAllUserData()
        if (links.isNotEmpty()) linkDao().upsertLinks(links)
        if (budgetState != null) budgetDao().upsertBudgetState(budgetState)
        if (events.isNotEmpty()) linkEventDao().insertEvents(events)
        if (sessions.isNotEmpty()) recommendationSessionDao().insertSessions(sessions)
    }

    companion object {
        private const val DATABASE_NAME = "link_ledger.db"

        @Volatile
        private var INSTANCE: LinkLedgerDatabase? = null

        fun getInstance(context: Context): LinkLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LinkLedgerDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
