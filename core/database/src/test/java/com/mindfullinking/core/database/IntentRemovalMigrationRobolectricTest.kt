package com.mindfullinking.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Verifies the v1 intent-bearing schema upgrades without deleting retained data. */
@RunWith(RobolectricTestRunner::class)
class IntentRemovalMigrationRobolectricTest {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "intent-removal-${System.nanoTime()}.db"
        databaseFile = context.getDatabasePath(name)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersionOneSchema(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        database = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
        databaseFile.delete()
    }

    @Test
    fun migrationRemovesIntentColumnsAndPreservesLinksAndSessions() {
        database.execSQL(
            """
            INSERT INTO links VALUES (
                'link-1', 'https://example.com', 'example.com', 'Example', 'keep note', 4, 15,
                'READ', NULL, 0, 0, 'ACTIVE', NULL, 1000, NULL, NULL, NULL, 2, NULL, 3, 2000,
                'MANUAL', 'DISABLED', 3000
            )
            """.trimIndent()
        )
        database.execSQL(
            "INSERT INTO recommendation_sessions VALUES ('session-1', 3, 'WATCH', 4000, '[\"link-1\"]')"
        )

        MIGRATION_1_2.migrate(database)
        MIGRATION_2_3.migrate(database)

        assertFalse(columnNames("links").contains("intent"))
        assertTrue(columnNames("links").contains("deadline_date_only"))
        assertFalse(columnNames("recommendation_sessions").contains("intent_filter"))

        database.query("SELECT title, note, priority, estimated_duration_minutes, recommendation_count FROM links WHERE id = 'link-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Example", cursor.getString(0))
            assertEquals("keep note", cursor.getString(1))
            assertEquals(4, cursor.getInt(2))
            assertEquals(15, cursor.getInt(3))
            assertEquals(3, cursor.getInt(4))
        }
        database.query("SELECT available_minutes, started_at_epoch_ms, recommended_link_ids_json FROM recommendation_sessions WHERE id = 'session-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals(4000L, cursor.getLong(1))
            assertEquals("[\"link-1\"]", cursor.getString(2))
        }
    }

    @Test
    fun roomOpenUpgradesARealVersionOneDatabaseWithoutDestructiveFallback() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "room-intent-removal-${System.nanoTime()}.db"
        val legacy = Room.databaseBuilder(context, LegacyIntentDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        legacy.openHelper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO links VALUES (
                    'link-2', 'https://example.org', 'example.org', 'Migrated', 'retained', 5, 3,
                    'ACT', NULL, 0, 0, 'ACTIVE', NULL, 5000, NULL, NULL, NULL, 0, NULL, 0, NULL,
                    'SHARE', 'DISABLED', 6000
                )
                """.trimIndent()
            )
            execSQL("INSERT INTO recommendation_sessions VALUES ('session-2', 3, 'READ', 7000, '[\"link-2\"]')")
        }
        legacy.close()

        val migrated = Room.databaseBuilder(context, LinkLedgerDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        migrated.openHelper.writableDatabase

        migrated.linkDao().getLinkById("link-2").also { link ->
            assertEquals("Migrated", link?.title)
            assertEquals(3, link?.estimatedDurationMinutes)
        }
        migrated.recommendationSessionDao().getLatestSession().also { session ->
            assertEquals("session-2", session?.id)
            assertEquals("[\"link-2\"]", session?.recommendedLinkIdsJson)
        }
        migrated.openHelper.writableDatabase.query("PRAGMA table_info(`links`)").use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
            assertFalse(names.contains("intent"))
            assertTrue(names.contains("deadline_date_only"))
        }
        migrated.close()
        context.deleteDatabase(name)
        }
    }

    private fun columnNames(table: String): Set<String> = buildSet {
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun createVersionOneSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE links (
                id TEXT NOT NULL PRIMARY KEY, canonical_url TEXT NOT NULL, display_url TEXT NOT NULL,
                title TEXT, note TEXT, priority INTEGER NOT NULL, estimated_duration_minutes INTEGER NOT NULL,
                intent TEXT NOT NULL, deadline_at_epoch_ms INTEGER, deadline_has_time INTEGER NOT NULL,
                deadline_notifications_enabled INTEGER NOT NULL, status TEXT NOT NULL, resolution_reason TEXT,
                saved_at_epoch_ms INTEGER NOT NULL, resolved_at_epoch_ms INTEGER, last_opened_at_epoch_ms INTEGER,
                snoozed_until_epoch_ms INTEGER, skip_count INTEGER NOT NULL, last_skipped_at_epoch_ms INTEGER,
                recommendation_count INTEGER NOT NULL, last_recommended_at_epoch_ms INTEGER,
                created_from TEXT NOT NULL, metadata_fetch_status TEXT NOT NULL, updated_at_epoch_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE recommendation_sessions (
                id TEXT NOT NULL PRIMARY KEY, available_minutes INTEGER NOT NULL, intent_filter TEXT,
                started_at_epoch_ms INTEGER NOT NULL, recommended_link_ids_json TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        LegacyIntentLinkEntity::class,
        BudgetStateEntity::class,
        LegacyIntentRecommendationSessionEntity::class,
        LinkEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LegacyIntentDatabase : RoomDatabase()

@androidx.room.Entity(
    tableName = "links",
    indices = [
        androidx.room.Index(value = ["canonical_url"], unique = false),
        androidx.room.Index(value = ["status", "snoozed_until_epoch_ms"]),
        androidx.room.Index(value = ["status", "priority", "deadline_at_epoch_ms"])
    ]
)
data class LegacyIntentLinkEntity(
    @androidx.room.PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "canonical_url") val canonicalUrl: String,
    @androidx.room.ColumnInfo(name = "display_url") val displayUrl: String,
    val title: String?,
    val note: String?,
    val priority: Int,
    @androidx.room.ColumnInfo(name = "estimated_duration_minutes") val estimatedDurationMinutes: Int,
    val intent: String,
    @androidx.room.ColumnInfo(name = "deadline_at_epoch_ms") val deadlineAtEpochMs: Long?,
    @androidx.room.ColumnInfo(name = "deadline_has_time") val deadlineHasTime: Boolean,
    @androidx.room.ColumnInfo(name = "deadline_notifications_enabled") val deadlineNotificationsEnabled: Boolean,
    val status: String,
    @androidx.room.ColumnInfo(name = "resolution_reason") val resolutionReason: String?,
    @androidx.room.ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMs: Long,
    @androidx.room.ColumnInfo(name = "resolved_at_epoch_ms") val resolvedAtEpochMs: Long?,
    @androidx.room.ColumnInfo(name = "last_opened_at_epoch_ms") val lastOpenedAtEpochMs: Long?,
    @androidx.room.ColumnInfo(name = "snoozed_until_epoch_ms") val snoozedUntilEpochMs: Long?,
    @androidx.room.ColumnInfo(name = "skip_count") val skipCount: Int,
    @androidx.room.ColumnInfo(name = "last_skipped_at_epoch_ms") val lastSkippedAtEpochMs: Long?,
    @androidx.room.ColumnInfo(name = "recommendation_count") val recommendationCount: Int,
    @androidx.room.ColumnInfo(name = "last_recommended_at_epoch_ms") val lastRecommendedAtEpochMs: Long?,
    @androidx.room.ColumnInfo(name = "created_from") val createdFrom: String,
    @androidx.room.ColumnInfo(name = "metadata_fetch_status") val metadataFetchStatus: String,
    @androidx.room.ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)

@androidx.room.Entity(tableName = "recommendation_sessions")
data class LegacyIntentRecommendationSessionEntity(
    @androidx.room.PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "available_minutes") val availableMinutes: Int,
    @androidx.room.ColumnInfo(name = "intent_filter") val intentFilter: String?,
    @androidx.room.ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
    @androidx.room.ColumnInfo(name = "recommended_link_ids_json") val recommendedLinkIdsJson: String
)
