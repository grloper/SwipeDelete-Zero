package com.swipedelete.zero

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.repository.*
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.*
import com.swipedelete.zero.domain.scanner.VideoMetadataExtractor
import com.swipedelete.zero.ui.navigation.Routes
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineViewModel
import com.swipedelete.zero.ui.screens.dual.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReviewViewModelConcurrencyTest {
    private fun item(id: Long) = MediaItem(id, Uri.parse("content://media/external/images/media/$id"),
        "$id.jpg", "image/jpeg", MediaType.IMAGE, 100L, 0L)

    @Test fun `delayed stage rejects mixed gestures and deck load then clears cross deck undo`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val decks = mock(DeckRepository::class.java)
            val staging = mock(StagingRepository::class.java)
            val backup = mock(BackupRepository::class.java)
            val stats = mock(StatsStore::class.java)
            val photos = mock(PhotosArchive::class.java)
            val first = Deck("first", DeckKind.SCREENSHOTS, "First", "", listOf(item(1), item(2)))
            val next = Deck("next", DeckKind.SCREENSHOTS, "Next", "", listOf(item(3)))
            `when`(decks.getDeck("first")).thenReturn(first)
            `when`(decks.getDeck("next")).thenReturn(next)
            `when`(stats.coachmarkSeen).thenReturn(flowOf(true))
            `when`(photos.queue).thenReturn(flowOf(emptyMap()))
            `when`(backup.observeBackedUpUris()).thenReturn(flowOf(emptyList()))
            val pending = CompletableDeferred<Unit>()
            doAnswer { call ->
                val continuation = call.rawArguments.last() as Continuation<Unit>
                backgroundScope.launch { pending.await(); continuation.resume(Unit) }
                COROUTINE_SUSPENDED
            }.`when`(staging).stage(first.items[0], "first")
            val sound = ReviewSound(RuntimeEnvironment.getApplication()).apply { setEnabled(false) }
            val vm = SwipeEngineViewModel(decks, staging, mock(ExclusionRepository::class.java), backup,
                photos, mock(VideoMetadataExtractor::class.java), mock(MediaAnalysisDao::class.java),
                mock(MediaPreloader::class.java), stats, sound, SavedStateHandle(mapOf(Routes.ARG_DECK_ID to "first")))
            runCurrent()
            vm.onSwipe(SwipeDirection.LEFT)
            runCurrent()
            vm.onSwipe(SwipeDirection.RIGHT)
            vm.loadDeck("next")
            vm.undo()
            runCurrent()
            assertEquals(0, vm.state.value.cursor)
            verify(decks, never()).getDeck("next")
            verify(backup, never()).recordKept(first.items[0], false)
            pending.complete(Unit)
            runCurrent()
            assertEquals(1, vm.state.value.cursor)
            assertEquals(100L, vm.state.value.sessionStagedBytes)
            vm.loadDeck("next")
            runCurrent()
            assertNull(vm.state.value.lastAction)
            assertEquals(0L, vm.state.value.sessionStagedBytes)
            vm.undo()
            runCurrent()
            assertEquals("next", vm.state.value.deck?.id)
            assertEquals(0, vm.state.value.cursor)
            verify(staging, never()).restore(first.items[0].contentUri.toString())
            doThrow(IllegalStateException("stage failed")).`when`(staging).stage(next.items[0], "next")
            vm.onSwipe(SwipeDirection.LEFT)
            runCurrent()
            assertEquals(0, vm.state.value.cursor)
            assertEquals(0L, vm.state.value.sessionStagedBytes)
            assertNotNull(vm.state.value.actionError)
            vm.onSwipe(SwipeDirection.RIGHT)
            runCurrent()
            assertEquals(1, vm.state.value.cursor)
            assertNull(vm.state.value.actionError)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `delayed comparison double tap advances exactly one pair`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val decks = mock(DeckRepository::class.java)
            val staging = mock(StagingRepository::class.java)
            val backup = mock(BackupRepository::class.java)
            val first = ComparisonPair(item(10), item(11))
            val next = ComparisonPair(item(12), item(13))
            `when`(decks.getComparisonPairs("duplicates")).thenReturn(listOf(first, next))
            val pending = CompletableDeferred<Unit>()
            doAnswer { call ->
                val continuation = call.rawArguments.last() as Continuation<Unit>
                backgroundScope.launch { pending.await(); continuation.resume(Unit) }
                COROUTINE_SUSPENDED
            }.`when`(staging).stage(first.secondary, "duplicates")
            val vm = DualCardViewModel(decks, staging, backup, SavedStateHandle(mapOf(Routes.ARG_DECK_ID to "duplicates")))
            runCurrent()
            vm.act(CompareAction.KEEP_A_TRASH_B)
            runCurrent()
            vm.act(CompareAction.KEEP_B_TRASH_A)
            runCurrent()
            assertEquals(0, vm.state.value.index)
            pending.complete(Unit)
            runCurrent()
            assertEquals(1, vm.state.value.index)
            assertEquals(next, vm.state.value.current)
            verify(staging, times(1)).stage(first.secondary, "duplicates")
            verify(staging, never()).stage(first.primary, "duplicates")
        } finally { Dispatchers.resetMain() }
    }
}
