package com.websarva.wings.android.slevo.ui.bbsroute

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * スクロール位置保存の純粋ロジックと Flow 変換を検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollPositionPersistenceTest {

    @Test
    fun saveState_firstCallReturnsTrue() {
        val state = ScrollPositionSaveState()

        val result = state.shouldSave(ScrollPosition(index = 1, offset = 10))

        assertTrue(result)
    }

    @Test
    fun saveState_samePositionReturnsFalse() {
        val state = ScrollPositionSaveState()
        val position = ScrollPosition(index = 1, offset = 10)
        state.shouldSave(position)

        val result = state.shouldSave(position)

        assertFalse(result)
    }

    @Test
    fun saveState_differentIndexReturnsTrue() {
        val state = ScrollPositionSaveState()
        state.shouldSave(ScrollPosition(index = 1, offset = 10))

        val result = state.shouldSave(ScrollPosition(index = 2, offset = 10))

        assertTrue(result)
    }

    @Test
    fun saveState_differentOffsetReturnsTrue() {
        val state = ScrollPositionSaveState()
        state.shouldSave(ScrollPosition(index = 1, offset = 10))

        val result = state.shouldSave(ScrollPosition(index = 1, offset = 20))

        assertTrue(result)
    }

    @Test
    fun saveState_afterDifferentThenSameReturnsTrue() {
        val state = ScrollPositionSaveState()
        val first = ScrollPosition(index = 1, offset = 10)
        state.shouldSave(first)
        state.shouldSave(ScrollPosition(index = 2, offset = 10))

        // 前回保存位置と異なるため、連続重複判定では保存対象となる。
        val result = state.shouldSave(first)

        assertTrue(result)
    }

    @Test
    fun scrollPositionsForPersistence_emitsLatestAtEachInterval() = runTest {
        val source = MutableSharedFlow<ScrollPosition>(extraBufferCapacity = 10)
        val emitted = mutableListOf<ScrollPosition>()

        val job = launch {
            source.scrollPositionsForPersistence(intervalMillis = 200L)
                .collect { emitted.add(it) }
        }

        source.emit(ScrollPosition(index = 0, offset = 0))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 1, offset = 5))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 2, offset = 10))
        advanceTimeBy(150) // 区間 0..200 の最後の値が emit される

        assertEquals(1, emitted.size)
        assertEquals(ScrollPosition(index = 2, offset = 10), emitted[0])

        job.cancel()
    }

    @Test
    fun scrollPositionsForPersistence_skipsDuplicatesWithinInterval() = runTest {
        val source = MutableSharedFlow<ScrollPosition>(extraBufferCapacity = 10)
        val emitted = mutableListOf<ScrollPosition>()

        val job = launch {
            source.scrollPositionsForPersistence(intervalMillis = 200L)
                .collect { emitted.add(it) }
        }

        source.emit(ScrollPosition(index = 1, offset = 0))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 1, offset = 0))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 1, offset = 0))
        advanceTimeBy(150)

        assertEquals(1, emitted.size)
        assertEquals(ScrollPosition(index = 1, offset = 0), emitted[0])

        job.cancel()
    }

    @Test
    fun scrollPositionsForPersistence_distinctUntilChangedCompressesDuplicates() = runTest {
        val source = MutableSharedFlow<ScrollPosition>(extraBufferCapacity = 10)
        val emitted = mutableListOf<ScrollPosition>()

        val job = launch {
            source.scrollPositionsForPersistence(intervalMillis = 200L)
                .collect { emitted.add(it) }
        }

        source.emit(ScrollPosition(index = 1, offset = 0))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 1, offset = 0))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 2, offset = 0))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 2, offset = 0))
        advanceTimeBy(150) // t=300 で 1 回目の sample(200L) が発火

        // 2 回目の sample 区間を進める
        source.emit(ScrollPosition(index = 3, offset = 0))
        advanceTimeBy(50)
        source.emit(ScrollPosition(index = 3, offset = 0))
        advanceTimeBy(200) // t=550 で 2 回目の sample が発火

        // distinctUntilChanged により同一位置は圧縮され、sample 区間内の最後の値が emit される
        assertEquals(2, emitted.size)
        assertEquals(ScrollPosition(index = 2, offset = 0), emitted[0])
        assertEquals(ScrollPosition(index = 3, offset = 0), emitted[1])

        job.cancel()
    }
}
