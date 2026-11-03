package com.mindfullinking.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mindfullinking.core.domain.BudgetResolution
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runnable local equivalent of the Room transaction contract tests. */
@RunWith(RobolectricTestRunner::class)
class BudgetMutationStoreRobolectricTest {
    private lateinit var database: LinkLedgerDatabase
    private lateinit var store: BudgetMutationStore
    private val now = Instant.parse("2026-08-24T00:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LinkLedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        store = BudgetMutationStore(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun invalidDurationAndFullBudgetPriorityIncreaseLeaveNoPersistentChanges() = runBlocking {
        val durationTarget = link(duration = 15)
        database.linkDao().upsertLink(durationTarget.toEntity())
        assertEquals(BudgetMutationResult.InvalidDuration, store.updateActiveLinkDuration(durationTarget.id, 17, now))
        assertEquals(15, database.linkDao().getLinkById(durationTarget.id)!!.estimatedDurationMinutes)

        reset()
        seedActivePoints(99)
        val priorityTarget = link(priority = Priority.LOWEST)
        database.linkDao().upsertLink(priorityTarget.toEntity())
        assertEquals(BudgetMutationResult.BudgetExceeded, store.updateActiveLinkPriority(priorityTarget.id, Priority.HIGHEST, now))
        assertEquals(Priority.LOWEST.value, database.linkDao().getLinkById(priorityTarget.id)!!.priority)
        assertTrue(database.linkEventDao().getAllEvents().isEmpty())
    }

    @Test
    fun saveAndRestoreAtExactCapacityHonorArchivedQuarterPointAndScheduleDeadlines() = runBlocking {
        seedActivePoints(99)
        val saveWithDeadline = link(
            priority = Priority.LOWEST,
            notificationsEnabled = true
        ).copy(deadlineAt = now.plus(3, ChronoUnit.DAYS))
        val save = store.saveActiveLink(saveWithDeadline, now = now) as BudgetMutationResult.Success
        assertTrue(save.notificationEffects.contains(NotificationEffect.ScheduleDeadline(saveWithDeadline)))

        reset()
        seedActivePoints(99)
        val archived = link(
            priority = Priority.LOWEST,
            status = LinkStatus.ARCHIVED,
            notificationsEnabled = true
        ).copy(deadlineAt = now.plus(3, ChronoUnit.DAYS))
        database.linkDao().upsertLink(archived.toEntity())
        val restore = store.restoreArchivedLink(archived.id, now = now) as BudgetMutationResult.Success
        assertEquals(10000, usedCents())
        assertTrue(restore.notificationEffects.contains(NotificationEffect.ScheduleDeadline(restore.link)))
    }

    @Test
    fun updatingAnExistingLinkThatDisablesDeadlineRemindersEmitsCancelEffect() = runBlocking {
        val scheduled = link(
            notificationsEnabled = true
        ).copy(deadlineAt = now.plus(3, ChronoUnit.DAYS))
        database.linkDao().upsertLink(scheduled.toEntity())

        val result = store.saveActiveLink(
            scheduled.copy(deadlineNotificationsEnabled = false),
            now = now
        ) as BudgetMutationResult.Success

        assertEquals(false, result.link.deadlineNotificationsEnabled)
        assertTrue(result.notificationEffects.contains(NotificationEffect.CancelDeadline(scheduled.id)))
        assertTrue(result.notificationEffects.none { it is NotificationEffect.ScheduleDeadline })
    }

    @Test
    fun everyResolutionCommitsExpectedStateEventAndPostCommitEffect() = runBlocking {
        assertFreeUp(BudgetResolution.FreeUpAction.RESOLVE, LinkStatus.RESOLVED, LinkEventType.RESOLVE)
        reset()
        assertFreeUp(BudgetResolution.FreeUpAction.ARCHIVE, LinkStatus.ARCHIVED, LinkEventType.ARCHIVE)
        reset()
        seedActivePoints(100)
        val deleted = database.linkDao().getActiveLinks().first().toDomainModel()
        val delete = store.saveActiveLink(
            link(priority = Priority.HIGHEST),
            BudgetResolution.FreeUpLink(deleted.id, BudgetResolution.FreeUpAction.DELETE), now
        ) as BudgetMutationResult.Success
        assertNull(database.linkDao().getLinkById(deleted.id))
        assertEvent(deleted.id, LinkEventType.DELETE)
        assertTrue(delete.notificationEffects.contains(NotificationEffect.CancelDeadline(deleted.id)))

        reset()
        seedActivePoints(100)
        val lowered = database.linkDao().getActiveLinks().first().toDomainModel()
        store.saveActiveLink(
            link(priority = Priority.HIGH), BudgetResolution.LowerPriority(lowered.id, Priority.LOWEST), now
        )
        assertEquals(Priority.LOWEST.value, database.linkDao().getLinkById(lowered.id)!!.priority)
        assertEvent(lowered.id, LinkEventType.PRIORITY_CHANGED)

        reset()
        seedActivePoints(99)
        val lowerProposed = store.saveActiveLink(
            link(priority = Priority.HIGHEST), BudgetResolution.LowerProposedPriority(Priority.LOWEST), now
        ) as BudgetMutationResult.Success
        assertEquals(Priority.LOWEST, lowerProposed.link.priority)

        reset()
        seedActivePoints(100)
        val replacement = database.linkDao().getActiveLinks().first().toDomainModel()
        val replace = store.saveActiveLink(
            link(priority = Priority.HIGHEST), BudgetResolution.ReplaceLink(replacement.id), now
        ) as BudgetMutationResult.Success
        assertNull(database.linkDao().getLinkById(replacement.id))
        assertEvent(replacement.id, LinkEventType.DELETE)
        assertTrue(replace.notificationEffects.contains(NotificationEffect.CancelDeadline(replacement.id)))

        reset()
        seedActivePoints(100)
        val overage = store.saveActiveLink(
            link(priority = Priority.HIGHEST), BudgetResolution.TemporaryOverage(5, 24), now
        ) as BudgetMutationResult.Success
        val state = database.budgetDao().getBudgetState()!!.toDomainModel()
        assertEquals(500, state.temporaryOverageCents)
        assertTrue(overage.notificationEffects.contains(NotificationEffect.ScheduleOverageExpiry(requireNotNull(state.temporaryOverageExpiresAt))))
    }

    @Test
    fun overCapacityAndStaleTargetResolutionsRollBackAndExpiredOverageDoesNotAddCapacity() = runBlocking {
        seedActivePoints(100)
        val target = database.linkDao().getActiveLinks().first().toDomainModel()
        val failed = store.saveActiveLink(
            link(priority = Priority.HIGHEST), BudgetResolution.FreeUpLink(target.id, BudgetResolution.FreeUpAction.ARCHIVE), now
        )
        assertEquals(BudgetMutationResult.BudgetExceeded, failed)
        assertEquals(LinkStatus.ACTIVE.name, database.linkDao().getLinkById(target.id)!!.status)
        assertTrue(database.linkEventDao().getAllEvents().isEmpty())

        val archived = link(status = LinkStatus.ARCHIVED)
        database.linkDao().upsertLink(archived.toEntity())
        val stale = store.saveActiveLink(
            link(priority = Priority.HIGHEST), BudgetResolution.FreeUpLink(archived.id, BudgetResolution.FreeUpAction.ARCHIVE), now
        )
        assertEquals(BudgetMutationResult.LinkIsNotActive, stale)
        assertTrue(stale !is BudgetMutationResult.Success)

        database.budgetDao().upsertBudgetState(
            BudgetState(temporaryOverageCents = 500, temporaryOverageExpiresAt = now.minusSeconds(1)).toEntity()
        )
        assertEquals(BudgetMutationResult.BudgetExceeded, store.saveActiveLink(link(), now = now))
    }

    @Test
    fun concurrentCapacityIncreasesOnlyCommitOneSuccessfulSave() = runBlocking {
        seedActivePoints(95)
        val results = listOf(
            async(Dispatchers.Default) { store.saveActiveLink(link(priority = Priority.HIGHEST), now = now) },
            async(Dispatchers.Default) { store.saveActiveLink(link(priority = Priority.HIGHEST), now = now) }
        ).awaitAll()
        assertEquals(1, results.count { it is BudgetMutationResult.Success })
        assertEquals(1, results.count { it == BudgetMutationResult.BudgetExceeded })
        assertEquals(10000, usedCents())
    }

    private suspend fun assertFreeUp(action: BudgetResolution.FreeUpAction, status: LinkStatus, event: LinkEventType) {
        seedActivePoints(100)
        val target = database.linkDao().getActiveLinks().first().toDomainModel()
        val result = store.saveActiveLink(
            link(priority = Priority.HIGH), BudgetResolution.FreeUpLink(target.id, action), now
        ) as BudgetMutationResult.Success
        assertEquals(status.name, database.linkDao().getLinkById(target.id)!!.status)
        assertEvent(target.id, event)
        assertTrue(result.notificationEffects.contains(NotificationEffect.CancelDeadline(target.id)))
    }

    private suspend fun seedActivePoints(points: Int) {
        val priorities = buildList {
            repeat(points / 5) { add(Priority.HIGHEST) }
            if (points % 5 != 0) add(Priority.fromInt(points % 5))
        }
        database.linkDao().upsertLinks(priorities.map { link(priority = it).toEntity() })
    }

    private suspend fun usedCents(): Int =
        database.linkDao().getActiveLinks().sumOf { it.priority * 100 } + database.linkDao().getArchivedCount() * 25

    private suspend fun assertEvent(linkId: String, event: LinkEventType) {
        assertEquals(setOf(event.name), database.linkEventDao().getAllEvents().filter { it.linkId == linkId }.map { it.eventType }.toSet())
    }

    private suspend fun reset() {
        database.linkEventDao().deleteAllEvents()
        database.linkDao().deleteAllLinks()
        database.budgetDao().resetBudgetState()
    }

    private fun link(
        priority: Priority = Priority.LOWEST,
        duration: Int = 15,
        status: LinkStatus = LinkStatus.ACTIVE,
        notificationsEnabled: Boolean = false
    ) = Link(
        id = UUID.randomUUID().toString(), canonicalUrl = "https://example.com/${UUID.randomUUID()}",
        displayUrl = "https://example.com", priority = priority, estimatedDurationMinutes = duration,
        deadlineNotificationsEnabled = notificationsEnabled, status = status, savedAt = now, updatedAt = now
    )
}
