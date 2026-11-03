package com.mindfullinking.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId

/**
 * Removes retired intent-category columns while preserving every link and
 * recommendation-session value that remains part of the product model.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `links_new` (
                `id` TEXT NOT NULL,
                `canonical_url` TEXT NOT NULL,
                `display_url` TEXT NOT NULL,
                `title` TEXT,
                `note` TEXT,
                `priority` INTEGER NOT NULL,
                `estimated_duration_minutes` INTEGER NOT NULL,
                `deadline_at_epoch_ms` INTEGER,
                `deadline_has_time` INTEGER NOT NULL,
                `deadline_notifications_enabled` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `resolution_reason` TEXT,
                `saved_at_epoch_ms` INTEGER NOT NULL,
                `resolved_at_epoch_ms` INTEGER,
                `last_opened_at_epoch_ms` INTEGER,
                `snoozed_until_epoch_ms` INTEGER,
                `skip_count` INTEGER NOT NULL,
                `last_skipped_at_epoch_ms` INTEGER,
                `recommendation_count` INTEGER NOT NULL,
                `last_recommended_at_epoch_ms` INTEGER,
                `created_from` TEXT NOT NULL,
                `metadata_fetch_status` TEXT NOT NULL,
                `updated_at_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `links_new` (
                `id`, `canonical_url`, `display_url`, `title`, `note`, `priority`,
                `estimated_duration_minutes`, `deadline_at_epoch_ms`, `deadline_has_time`,
                `deadline_notifications_enabled`, `status`, `resolution_reason`,
                `saved_at_epoch_ms`, `resolved_at_epoch_ms`, `last_opened_at_epoch_ms`,
                `snoozed_until_epoch_ms`, `skip_count`, `last_skipped_at_epoch_ms`,
                `recommendation_count`, `last_recommended_at_epoch_ms`, `created_from`,
                `metadata_fetch_status`, `updated_at_epoch_ms`
            )
            SELECT
                `id`, `canonical_url`, `display_url`, `title`, `note`, `priority`,
                `estimated_duration_minutes`, `deadline_at_epoch_ms`, `deadline_has_time`,
                `deadline_notifications_enabled`, `status`, `resolution_reason`,
                `saved_at_epoch_ms`, `resolved_at_epoch_ms`, `last_opened_at_epoch_ms`,
                `snoozed_until_epoch_ms`, `skip_count`, `last_skipped_at_epoch_ms`,
                `recommendation_count`, `last_recommended_at_epoch_ms`, `created_from`,
                `metadata_fetch_status`, `updated_at_epoch_ms`
            FROM `links`
            """.trimIndent()
        )
        database.execSQL("DROP TABLE `links`")
        database.execSQL("ALTER TABLE `links_new` RENAME TO `links`")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_links_canonical_url` ON `links` (`canonical_url`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_links_status_snoozed_until_epoch_ms` ON `links` (`status`, `snoozed_until_epoch_ms`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_links_status_priority_deadline_at_epoch_ms` ON `links` (`status`, `priority`, `deadline_at_epoch_ms`)")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recommendation_sessions_new` (
                `id` TEXT NOT NULL,
                `available_minutes` INTEGER NOT NULL,
                `started_at_epoch_ms` INTEGER NOT NULL,
                `recommended_link_ids_json` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `recommendation_sessions_new` (
                `id`, `available_minutes`, `started_at_epoch_ms`, `recommended_link_ids_json`
            )
            SELECT `id`, `available_minutes`, `started_at_epoch_ms`, `recommended_link_ids_json`
            FROM `recommendation_sessions`
            """.trimIndent()
        )
        database.execSQL("DROP TABLE `recommendation_sessions`")
        database.execSQL("ALTER TABLE `recommendation_sessions_new` RENAME TO `recommendation_sessions`")
    }
}

/**
 * Persists date-only deadlines as ISO local dates so changing the device time
 * zone cannot shift a deadline to a different calendar day. Existing values
 * are converted in the device zone at migration time; newly saved links carry
 * the authoritative date explicitly.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `links` ADD COLUMN `deadline_date_only` TEXT")

        database.query(
            "SELECT `id`, `deadline_at_epoch_ms` FROM `links` " +
                "WHERE `deadline_has_time` = 0 AND `deadline_at_epoch_ms` IS NOT NULL"
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val deadlineIndex = cursor.getColumnIndexOrThrow("deadline_at_epoch_ms")
            while (cursor.moveToNext()) {
                val localDate = Instant.ofEpochMilli(cursor.getLong(deadlineIndex))
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
                database.execSQL(
                    "UPDATE `links` SET `deadline_date_only` = ? WHERE `id` = ?",
                    arrayOf(localDate, cursor.getString(idIndex))
                )
            }
        }
    }
}
