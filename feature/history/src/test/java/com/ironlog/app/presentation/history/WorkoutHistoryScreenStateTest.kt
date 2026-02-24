package com.ironlog.app.presentation.history

import androidx.paging.LoadState
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHistoryScreenStateTest {

    @Test
    fun `resolveHistoryContentState returns loading when refresh is loading and no items`() {
        val state = resolveHistoryContentState(
            refreshLoadState = LoadState.Loading,
            itemCount = 0
        )

        assertEquals(HistoryListContentState.Loading, state)
    }

    @Test
    fun `resolveHistoryContentState returns error when refresh is error and no items`() {
        val state = resolveHistoryContentState(
            refreshLoadState = LoadState.Error(IllegalStateException("boom")),
            itemCount = 0
        )

        assertEquals(HistoryListContentState.Error, state)
    }

    @Test
    fun `resolveHistoryContentState returns empty when refresh is not loading and no items`() {
        val state = resolveHistoryContentState(
            refreshLoadState = LoadState.NotLoading(endOfPaginationReached = false),
            itemCount = 0
        )

        assertEquals(HistoryListContentState.Empty, state)
    }

    @Test
    fun `resolveHistoryContentState returns content when items are present`() {
        val state = resolveHistoryContentState(
            refreshLoadState = LoadState.Error(IllegalStateException("boom")),
            itemCount = 3
        )

        assertEquals(HistoryListContentState.Content, state)
    }
}
