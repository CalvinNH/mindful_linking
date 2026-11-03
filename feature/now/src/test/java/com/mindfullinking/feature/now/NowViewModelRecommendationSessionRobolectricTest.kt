package com.mindfullinking.feature.now

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Verifies a failed earlier write cannot erase a later completed auction's immediate history. */
@RunWith(RobolectricTestRunner::class)
class NowViewModelRecommendationSessionRobolectricTest {
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
    fun failedAuctionAAfterAuctionBIsQueuedKeepsAndPersistsAuctionBHistory() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = NowViewModel(application, database) { session ->
            if (session.availableMinutes == 1) {
                delay(100)
                error("Simulated persistence failure for auction A")
            }
            database.recommendationSessionDao().insertCompletedSession(session)
        }

        viewModel.recordCompletedAuction(availableMinutes = 1, recommendedLinkIds = listOf("a-link"))
        viewModel.recordCompletedAuction(availableMinutes = 3, recommendedLinkIds = listOf("b-link"))

        delay(300)

        assertEquals(listOf("b-link"), viewModel.uiState.value.lastSessionRecommendedLinkIds)
        assertEquals(emptyList<String>(), viewModel.uiState.value.previousSessionRecommendedLinkIds)
        assertEquals(
            listOf("b-link"),
            database.recommendationSessionDao().getLatestSession()?.toDomainModel()?.recommendedLinkIds
        )
        assertEquals(1, database.recommendationSessionDao().getMostRecentSessions(limit = 2).size)
    }
}
