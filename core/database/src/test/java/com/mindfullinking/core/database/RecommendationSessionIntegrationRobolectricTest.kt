package com.mindfullinking.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mindfullinking.core.domain.RecommendationEngine
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.model.RecommendationSession
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Exercises the persisted session history that the auction supplies to the deterministic engine. */
@RunWith(RobolectricTestRunner::class)
class RecommendationSessionIntegrationRobolectricTest {
    private lateinit var database: LinkLedgerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), LinkLedgerDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun persistedCompletedSessionsDriveTheNextAuctionExclusionDeterministically() = runBlocking {
        val links = listOf("one", "two", "three", "four").mapIndexed { index, id ->
            Link(
                id = id,
                canonicalUrl = "https://example.com/$id",
                displayUrl = id,
                priority = if (id == "two") Priority.HIGHEST else Priority.MEDIUM,
                estimatedDurationMinutes = 3,
                savedAt = Instant.parse("2026-08-01T00:00:00Z").plusSeconds(index.toLong())
            )
        }
        database.linkDao().upsertLinks(links.map { it.toEntity() })
        database.recommendationSessionDao().insertSession(
            RecommendationSession(
                id = "session-a", availableMinutes = 3,
                startedAt = Instant.parse("2026-08-20T10:00:00Z"),
                recommendedLinkIds = listOf("one")
            ).toEntity()
        )
        database.recommendationSessionDao().insertSession(
            RecommendationSession(
                id = "session-b", availableMinutes = 3,
                startedAt = Instant.parse("2026-08-20T11:00:00Z"),
                recommendedLinkIds = listOf("two")
            ).toEntity()
        )

        val history = database.recommendationSessionDao().getMostRecentSessions(2).map { it.toDomainModel() }
        assertEquals(listOf("session-b", "session-a"), history.map { it.id })

        val nextResult = RecommendationEngine.recommend(
            activeLinks = database.linkDao().getActiveLinks().map { it.toDomainModel() },
            availableMinutes = 3,
            lastSessionRecommendedLinkIds = history[0].recommendedLinkIds,
            previousSessionRecommendedLinkIds = history[1].recommendedLinkIds,
            now = Instant.parse("2026-08-24T00:00:00Z")
        )

        assertFalse(nextResult.recommendations.any { it.link.id == "two" })
        assertEquals(3, nextResult.recommendations.size)
    }

    @Test
    fun latestSessionUsesIdAsTheDeterministicTieBreaker() = runBlocking {
        val timestamp = Instant.parse("2026-08-20T11:00:00Z")
        database.recommendationSessionDao().insertSession(
            RecommendationSession("auction-a", 3, timestamp, listOf("one")).toEntity()
        )
        database.recommendationSessionDao().insertSession(
            RecommendationSession("auction-z", 3, timestamp, listOf("two")).toEntity()
        )

        assertEquals("auction-z", database.recommendationSessionDao().getLatestSession()?.id)
    }

    @Test
    fun completedAuctionPersistenceRetainsOnlyTheTwoHistoriesUsedByTheEngine() = runBlocking {
        val dao = database.recommendationSessionDao()
        listOf("auction-1", "auction-2", "auction-3").forEachIndexed { index, id ->
            dao.insertCompletedSession(
                RecommendationSession(
                    id = id,
                    availableMinutes = 3,
                    startedAt = Instant.parse("2026-08-20T10:00:00Z").plusSeconds(index.toLong()),
                    recommendedLinkIds = listOf("link-$index")
                ).toEntity()
            )
        }

        assertEquals(
            listOf("auction-3", "auction-2"),
            dao.getMostRecentSessions(limit = 10).map { it.id }
        )
    }
}
