package com.mindfullinking.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LinkEntity::class,
        BudgetStateEntity::class,
        RecommendationSessionEntity::class,
        LinkEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LinkLedgerDatabase : RoomDatabase() {

    abstract fun linkDao(): LinkDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recommendationSessionDao(): RecommendationSessionDao
    abstract fun linkEventDao(): LinkEventDao

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
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
