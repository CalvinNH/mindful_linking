package com.mindfullinking.feature.settings

import com.mindfullinking.core.database.BudgetStateEntity
import com.mindfullinking.core.database.LinkEntity
import com.mindfullinking.core.database.LinkEventEntity
import com.mindfullinking.core.database.RecommendationSessionEntity
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EncryptedBackupCodecTest {
    @Test
    fun encryptedSnapshot_roundTripsEveryPersistedField() {
        val snapshot = BackupSnapshot(
            exportedAtEpochMs = 42L,
            links = listOf(
                LinkEntity(
                    id = "link", canonicalUrl = "https://example.com?a=1", displayUrl = "example.com", title = "Title", note = "Private note",
                    priority = 5, estimatedDurationMinutes = 3, deadlineAtEpochMs = 123L, deadlineDateOnly = "2026-08-25",
                    deadlineHasTime = false, deadlineNotificationsEnabled = true, status = "ACTIVE", resolutionReason = null,
                    savedAtEpochMs = 1L, resolvedAtEpochMs = null, lastOpenedAtEpochMs = 2L, snoozedUntilEpochMs = 3L,
                    skipCount = 4, lastSkippedAtEpochMs = 5L, recommendationCount = 6, lastRecommendedAtEpochMs = 7L,
                    createdFrom = "SHARE", metadataFetchStatus = "DISABLED", updatedAtEpochMs = 8L
                )
            ),
            budgetState = BudgetStateEntity(1, 10_000, 500, 9L, 10L),
            events = listOf(LinkEventEntity("event", "link", "SAVE", 11L, "details")),
            sessions = listOf(RecommendationSessionEntity("session", 15, 12L, "[\"link\"]"))
        )
        val passphrase = "correct horse battery staple".toCharArray()

        val encrypted = EncryptedBackupCodec.encrypt(snapshot, passphrase)
        val decrypted = EncryptedBackupCodec.decrypt(encrypted, passphrase)

        assertFalse(encrypted.decodeToString().contains("Private note"))
        assertEquals(snapshot, decrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun incorrectPassphrase_cannotDecryptBackup() {
        val encrypted = EncryptedBackupCodec.encrypt(
            BackupSnapshot(1L, emptyList(), null, emptyList(), emptyList()),
            "correct".toCharArray()
        )
        EncryptedBackupCodec.decrypt(encrypted, "incorrect".toCharArray())
    }

    @Test
    fun malformedOrWrongPassphrase_neverInvokesReplacement() = runBlocking {
        val validContainer = EncryptedBackupCodec.encrypt(
            BackupSnapshot(1L, emptyList(), null, emptyList(), emptyList()),
            "correct".toCharArray()
        )
        var replacementCalls = 0
        val restorer = ValidatedBackupRestorer { replacementCalls++ }

        runCatching { restorer.restore(validContainer, "incorrect".toCharArray()) }
        runCatching { restorer.restore(byteArrayOf(1, 2, 3), "correct".toCharArray()) }

        assertEquals(0, replacementCalls)
    }

    @Test
    fun authenticatedMalformedDeadline_neverInvokesReplacement() = runBlocking {
        val authenticatedButInvalid = EncryptedBackupCodec.encrypt(
            BackupSnapshot(
                exportedAtEpochMs = 1L,
                links = listOf(
                    LinkEntity(
                        id = "bad-deadline", canonicalUrl = "https://example.com", displayUrl = "example.com", title = null, note = null,
                        priority = 1, estimatedDurationMinutes = 1, deadlineAtEpochMs = 1L, deadlineDateOnly = "not-a-date",
                        deadlineHasTime = false, deadlineNotificationsEnabled = true, status = "ACTIVE", resolutionReason = null,
                        savedAtEpochMs = 1L, resolvedAtEpochMs = null, lastOpenedAtEpochMs = null, snoozedUntilEpochMs = null,
                        skipCount = 0, lastSkippedAtEpochMs = null, recommendationCount = 0, lastRecommendedAtEpochMs = null,
                        createdFrom = "MANUAL", metadataFetchStatus = "DISABLED", updatedAtEpochMs = 1L
                    )
                ),
                budgetState = null, events = emptyList(), sessions = emptyList()
            ),
            "correct".toCharArray()
        )
        var replacementCalls = 0

        runCatching {
            ValidatedBackupRestorer { replacementCalls++ }
                .restore(authenticatedButInvalid, "correct".toCharArray())
        }

        assertEquals(0, replacementCalls)
    }

    @Test
    fun authenticatedInvalidOverage_neverInvokesReplacement() = runBlocking {
        val authenticatedButInvalid = EncryptedBackupCodec.encrypt(
            BackupSnapshot(
                exportedAtEpochMs = 1L,
                links = emptyList(),
                budgetState = BudgetStateEntity(1, 10_000, 150, 10L, null),
                events = emptyList(),
                sessions = emptyList()
            ),
            "correct".toCharArray()
        )
        var replacementCalls = 0

        runCatching {
            ValidatedBackupRestorer { replacementCalls++ }
                .restore(authenticatedButInvalid, "correct".toCharArray())
        }

        assertEquals(0, replacementCalls)
    }

    @Test
    fun wipeExecutor_cancelsNotificationsOnlyAfterDataClear() = runBlocking {
        val calls = mutableListOf<String>()
        DataWipeExecutor(
            clearPersistedData = { calls += "clear-data" },
            cancelNotifications = { calls += "cancel-notifications" },
            clearPreferences = { calls += "clear-preferences" }
        ).wipe()

        assertEquals(listOf("clear-data", "cancel-notifications", "clear-preferences"), calls)
    }

    @Test
    fun restoredReminderPlan_includesActiveDeadlineAndTemporaryOverage() {
        val snapshot = BackupSnapshot(
            exportedAtEpochMs = 1L,
            links = listOf(
                LinkEntity(
                    id = "deadline", canonicalUrl = "https://example.com", displayUrl = "example.com", title = null, note = null,
                    priority = 1, estimatedDurationMinutes = 1, deadlineAtEpochMs = 1234L, deadlineDateOnly = "2026-08-25",
                    deadlineHasTime = false, deadlineNotificationsEnabled = true, status = "ACTIVE", resolutionReason = null,
                    savedAtEpochMs = 1L, resolvedAtEpochMs = null, lastOpenedAtEpochMs = null, snoozedUntilEpochMs = null,
                    skipCount = 0, lastSkippedAtEpochMs = null, recommendationCount = 0, lastRecommendedAtEpochMs = null,
                    createdFrom = "MANUAL", metadataFetchStatus = "DISABLED", updatedAtEpochMs = 1L
                )
            ),
            budgetState = BudgetStateEntity(1, 10_000, 100, 55L, null),
            events = emptyList(), sessions = emptyList()
        )

        val plan = snapshot.restoredReminderPlan()

        assertEquals(listOf("deadline"), plan.deadlineLinks.map { it.id })
        assertEquals(55L, plan.overageExpiresAt!!.toEpochMilli())
    }
}
