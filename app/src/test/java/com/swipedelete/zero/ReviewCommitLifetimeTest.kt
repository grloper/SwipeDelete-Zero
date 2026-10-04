package com.swipedelete.zero

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.repository.*
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.*
import com.swipedelete.zero.domain.scanner.VideoMetadataExtractor
import com.swipedelete.zero.ui.navigation.Routes
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReviewCommitLifetimeTest {
    private data class Fixture(val vm: SwipeEngineViewModel, val decks: DeckRepository, val staging: StagingRepository, val deck: Deck)
    private suspend fun fixture(): Fixture {
        val item = MediaItem(1, Uri.parse("content://media/external/images/media/1"), "fixture.png", "image/png", MediaType.IMAGE, 100L, 0L)
        val deck = Deck("first", DeckKind.SCREENSHOTS, "First", "", listOf(item, item.copy(id = 2)))
        val decks = mock(DeckRepository::class.java)
        val staging = mock(StagingRepository::class.java)
        val backup = mock(BackupRepository::class.java)
        val stats = mock(StatsStore::class.java)
        val photos = mock(PhotosArchive::class.java)
        `when`(decks.getDeck("first")).thenReturn(deck)
        `when`(stats.coachmarkSeen).thenReturn(flowOf(true))
        `when`(photos.queue).thenReturn(flowOf(emptyMap()))
        `when`(backup.observeBackedUpUris()).thenReturn(flowOf(emptyList()))
        val sound = ReviewSound(RuntimeEnvironment.getApplication()).apply { setEnabled(false) }
        val vm = SwipeEngineViewModel(decks, staging, mock(ExclusionRepository::class.java), backup,
            photos, mock(VideoMetadataExtractor::class.java), mock(MediaAnalysisDao::class.java),
            mock(MediaPreloader::class.java), stats, sound, SavedStateHandle(mapOf(Routes.ARG_DECK_ID to "first")))
        return Fixture(vm, decks, staging, deck)
    }

    @Test fun `accepted stage completes progress once after ViewModel is cleared`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = fixture()
            val store = ViewModelStore().apply { put("review", f.vm) }
            runCurrent()
            val release = CompletableDeferred<Unit>()
            var completedWrites = 0
            doAnswer { call ->
                val write: suspend () -> Unit = { release.await(); completedWrites++ }
                write.startCoroutineUninterceptedOrReturn(call.rawArguments.last() as Continuation<Unit>)
            }.`when`(f.decks).saveProgress(f.deck, 1)
            assertTrue(f.vm.onSwipe(SwipeDirection.LEFT))
            runCurrent()
            verify(f.staging, times(1)).stage(f.deck.items[0], "first")
            store.clear()
            assertFalse("Disposed route cannot admit another decision", f.vm.onSwipe(SwipeDirection.RIGHT))
            release.complete(Unit)
            runCurrent()
            assertEquals(1, completedWrites)
            assertEquals(1, f.vm.state.value.cursor)
            assertFalse(f.vm.state.value.actionInProgress)
            assertFalse("Cleared VM rejects admission even after the busy gate opens", f.vm.onSwipe(SwipeDirection.RIGHT))
            verify(f.decks, times(1)).saveProgress(f.deck, 1)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `accepted undo completes restored cursor once after ViewModel is cleared`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val f = fixture()
            val store = ViewModelStore().apply { put("review", f.vm) }
            runCurrent()
            assertTrue(f.vm.onSwipe(SwipeDirection.LEFT))
            runCurrent()
            val release = CompletableDeferred<Unit>()
            var completedWrites = 0
            doAnswer { call ->
                val write: suspend () -> Unit = { release.await(); completedWrites++ }
                write.startCoroutineUninterceptedOrReturn(call.rawArguments.last() as Continuation<Unit>)
            }.`when`(f.decks).saveProgress(f.deck, 0)
            f.vm.undo()
            runCurrent()
            store.clear()
            f.vm.undo()
            release.complete(Unit)
            runCurrent()
            assertEquals(1, completedWrites)
            assertEquals(0, f.vm.state.value.cursor)
            assertNull(f.vm.state.value.lastAction)
            assertFalse(f.vm.state.value.actionInProgress)
            assertFalse("Cleared VM rejects admission after Undo finishes", f.vm.onSwipe(SwipeDirection.RIGHT))
            verify(f.staging, times(1)).restore(f.deck.items[0].contentUri.toString())
            verify(f.decks, times(1)).saveProgress(f.deck, 0)
        } finally { Dispatchers.resetMain() }
    }
}
