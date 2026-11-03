package com.mindfullinking.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mindfullinking.core.domain.BudgetResolution
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises budget-changing writes against real Room transactions. */
@RunWith(AndroidJUnit4::class)
class BudgetMutationStoreTest {
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
    fun tearDown() {
        database.close()
    }

    @Test
    fun invalidDurationsAreRejectedAtTheMutationBoundary() = runBlocking {
        val active = link(duration = 15)
        database.linkDao().upsertLink(active.toEntity())

        assertEquals(
            BudgetMutationResult.InvalidDuration,
            store.updateActiveLinkDuration(active.id, 17, now)
        )
        assertEquals(15, database.linkDao().getLinkById(active.id)!!.estimatedDurationMinutes)
        assertEquals(
            BudgetMutationResult.InvalidDuration,
            store.saveActiveLink(link(duration = 17), now = now)
        )
        assertEquals(1, database.linkDao().getAllLinks().size)
        assertTrue(database.linkEventDao().getAllEvents().isEmpty())
    }

    @Test
    fun fullBudgetPriorityIncreaseIsRejectedWithoutChangingLinksOrEvents() = runBlocking {
        val links = List(19) { link(priority = Priority.HIGHEST) } + link(priority = Priority.LOWEST)
        database.linkDao().upsertLinks(links.map(Link::toEntity))
        val target = links.last()

        assertEquals(
            BudgetMutationResult.BudgetExceeded,
            store.updateActiveLinkPriority(target.id, Priority.HIGHEST, now)
        )

        assertEquals(Priority.LOWEST.value, database.linkDao().getLinkById(target.id)!!.priority)
        assertTrue(database.linkEventDao().getAllEvents().isEmpty())
    }

    @Test
    fun saveAndRestoreAcceptExactCapacityIncludingArchivedQuarterPointCost() = runBlocking {
        seedActivePoints(99)
        val exactSave = link(priority = Priority.LOWEST)
        assertTrue(store.saveActiveLink(exactSave, now = now) is BudgetMutationResult.Success)

        database.linkDao().deleteAllLinks()
        seedActivePoints(99)
        val archived = link(priority = Priority.LOWEST, status = LinkStatus.ARCHIVED)
        database.linkDao().upsertLink(archived.toEntity())

        val result = store.restoreArchivedLink(archived.id, now = now)
        assertTrue(result is BudgetMutationResult.Success)
        assertEquals(LinkStatus.ACTIVE.name, database.linkDao().getLinkById(archived.id)!!.status)
        assertEquals(10000, usedCents())
    }

    @Test
    fun freeUpResolveArchivesAndDeletesCommitTheirStateEventsAndNotificationCancellation() = runBlocking {
        assertFreeUpResolution(
            action = BudgetResolution.FreeUpAction.RESOLVE,
            expectedStatus = LinkStatus.RESOLVED,
            expectedEvent = LinkEventType.RESOLVE
        )
        reset()
        assertFreeUpResolution(
            action = BudgetResolution.FreeUpAction.ARCHIVE,
            expectedStatus = LinkStatus.ARCHIVED,
            expectedEvent = LinkEventType.ARCHIVE
        )
        reset()
        seedActivePoints(100)
        val target = database.linkDao().getActiveLinks().first().toDomainModel()
        val proposed = link(priority = Priority.HIGHEST)
        val result = store.saveActiveLink(
            proposed,
            BudgetResolution.FreeUpLink(target.id, BudgetResolution.FreeUpAction.DELETE),
            now
        ) as BudgetMutationResult.Success

        assertNull(database.linkDao().getLinkById(target.id))
        assertEventTypes(target.id, LinkEventType.DELETE)
        assertTrue(result.notificationEffects.contains(NotificationEffect.CancelDeadline(target.id)))
    }

    @Test
    fun lowerReplaceAndTemporaryOverageResolutionsCommitExpectedStateAndEvents() = runBlocking {
        seedActivePoints(100)
        val lowerTarget = database.linkDao().getActiveLinks().first().toDomainModel()
        val lowered = store.saveActiveLink(
            link(priority = Priority.HIGH),
            BudgetResolution.LowerPriority(lowerTarget.id, Priority.LOWEST),
            now
        ) as BudgetMutationResult.Success
        assertEquals(Priority.LOWEST.value, database.linkDao().getLinkById(lowerTarget.id)!!.priority)
        assertEventTypes(lowerTarget.id, LinkEventType.PRIORITY_CHANGED)
        assertEquals(LinkEventType.SAVE.name, database.linkEventDao().getAllEvents().single { it.linkId == lowered.link.id }.eventType)

        reset()
        seedActivePoints(99)
        val lowerProposed = store.saveActiveLink(
            link(priority = Priority.HIGHEST),
            BudgetResolution.LowerProposedPriority(Priority.LOWEST),
            now
        ) as BudgetMutationResult.Success
        assertEquals(Priority.LOWEST, lowerProposed.link.priority)

        reset()
        seedActivePoints(100)
        val replacement = database.linkDao().getActiveLinks().first().toDomainModel()
        val replaceResult = store.saveActiveLink(
            link(priority = Priority.HIGHEST),
            BudgetResolution.ReplaceLink(replacement.id),
            now
        ) as BudgetMutationResult.Success
        assertNull(database.linkDao().getLinkById(replacement.id))
        assertEventTypes(replacement.id, LinkEventType.DELETE)
        assertTrue(replaceResult.notificationEffects.contains(NotificationEffect.CancelDeadline(replacement.id)))

        reset()
        seedActivePoints(100)
        val overageResult = store.saveActiveLink(
            link(priority = Priority.HIGHEST),
            BudgetResolution.TemporaryOverage(overagePoints = 5, durationHours = 24),
            now
        ) as BudgetMutationResult.Success
        val budget = database.budgetDao().getBudgetState()!!.toDomainModel()
        assertEquals(500, budget.temporaryOverageCents)
        assertEquals(now.plusSeconds(24 * 60 * 60), budget.temporaryOverageExpiresAt)
        assertTrue(overageResult.notificationEffects.contains(NotificationEffect.ScheduleOverageExpiry(requireNotNull(budget.temporaryOverageExpiresAt))))
    }

    @Test
    fun failedResolutionRollsBackResolutionStateEventsAndEffects() = runBlocking {
        seedActivePoints(100)
        val target = database.linkDao().getActiveLinks().first().toDomainModel()
        val result = store.saveActiveLink(
            link(priority = Priority.HIGHEST),
            BudgetResolution.FreeUpLink(target.id, BudgetResolution.FreeUpAction.ARCHIVE),
            now
        )

        assertEquals(BudgetMutationResult.BudgetExceeded, result)
        assertEquals(LinkStatus.ACTIVE.name, database.linkDao().getLinkById(target.id)!!.status)
        assertTrue(database.linkEventDao().getAllEvents().isEmpty())
        assertEquals(20, database.linkDao().getActiveLinks().size)
    }

    @Test
    fun staleOrIneligibleResolutionTargetsAreRejectedWithoutPartialWrites() = runBlocking {
        seedActivePoints(100)
        val archived = link(status = LinkStatus.ARCHIVED)
        database.linkDao().upsertLink(archived.toEntity())

        assertEquals(
            BudgetMutationResult.LinkIsNotActive,
            store.saveActiveLink(
                link(priority = Priority.HIGHEST),
                BudgetResolution.FreeUpLink(archived.id, BudgetResolution.FreeUpAction.ARCHIVE),
                now
            )
        )
        assertEquals(LinkStatus.ARCHIVED.name, database.linkDao().getLinkById(archived.id)!!.status)
        assertTrue(database.linkEventDao().getAllEvents().isEmpty())
    }

    @Test
    fun expiredOverageDoesNotIncreaseCapacity() = runBlocking {
        seedActivePoints(100)
        database.budgetDao().upsertBudgetState(
            BudgetState(
                temporaryOverageCents = 500,
                temporaryOverageExpiresAt = now.minusSeconds(1)
            ).toEntity()
        )

        assertEquals(BudgetMutationResult.BudgetExceeded, store.saveActiveLink(link(), now = now))
        assertEquals(20, database.linkDao().getActiveLinks().size)
    }

    @Test
    fun concurrentCapacityIncreasesCannotBothExceedTheBudget() = runBlocking {
        seedActivePoints(95)
        val results = listOf(
            async(Dispatchers.Default) { store.saveActiveLink(link(priority = Priority.HIGHEST), now = now) },
            async(Dispatchers.Default) { store.saveActiveLink(link(priority = Priority.HIGHEST), now = now) }
        ).awaitAll()

        assertEquals(1, results.count { it is BudgetMutationResult.Success })
        assertEquals(1, results.count { it == BudgetMutationResult.BudgetExceeded })
        assertEquals(10000, usedCents())
    }

    private suspend fun assertFreeUpResolution(
        action: BudgetResolution.FreeUpAction,
        expectedStatus: LinkStatus,
        expectedEvent: LinkEventType
    ) {
        seedActivePoints(100)
        val target = database.linkDao().getActiveLinks().first().toDomainModel()
        val result = store.saveActiveLink(
            link(priority = Priority.HIGH),
            BudgetResolution.FreeUpLink(target.id, action),
            now
        ) as BudgetMutationResult.Success

        assertEquals(expectedStatus.name, database.linkDao().getLinkById(target.id)!!.status)
        assertEventTypes(target.id, expectedEvent)
        assertTrue(result.notificationEffects.contains(NotificationEffect.CancelDeadline(target.id)))
    }

    private suspend fun seedActivePoints(points: Int) {
        require(points in 0..100)
        val priorities = buildList {
            repeat(points / 5) { add(Priority.HIGHEST) }
            when (val remainder = points % 5) {
                0 -> Unit
                else -> add(Priority.fromInt(remainder))
            }
        }
        database.linkDao().upsertLinks(priorities.map { link(priority = it).toEntity() })
    }

    private suspend fun usedCents(): Int {
        val active = database.linkDao().getActiveLinks().map { it.toDomainModel() }
        return active.sumOf { it.priority.value * 100 } + database.linkDao().getArchivedCount() * 25
    }

    private suspend fun assertEventTypes(linkId: String, vararg expected: LinkEventType) {
        assertEquals(
            expected.map(LinkEventType::name).toSet(),
            database.linkEventDao().getAllEvents().filter { it.linkId == linkId }.map { it.eventType }.toSet()
        )
    }

    private suspend fun reset() {
        database.linkEventDao().deleteAllEvents()
        database.linkDao().deleteAllLinks()
        database.budgetDao().resetBudgetState()
    }

    private fun link(
        priority: Priority = Priority.LOWEST,
        duration: Int = 15,
        status: LinkStatus = LinkStatus.ACTIVE
    ) = Link(
        id = UUID.randomUUID().toString(),
        canonicalUrl = "https://example.com/${UUID.randomUUID()}",
        displayUrl = "https://example.com",
        priority = priority,
        estimatedDurationMinutes = duration,
        status = status,
        savedAt = now,
        updatedAt = now
    )
}
