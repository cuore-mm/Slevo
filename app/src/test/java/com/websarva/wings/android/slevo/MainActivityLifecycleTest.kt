package com.websarva.wings.android.slevo

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * MainActivityのSTARTED境界がpending result観察の開始・停止へ一対一で変換されることを検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainActivityLifecycleTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** START、STOP、再STARTの各境界で観察callbackが一度ずつ呼ばれる。 */
    @Test
    fun startedLifecycle_startsAndStopsObservation() = runTest {
        val owner = TestLifecycleOwner()
        val callbacks = mutableListOf<String>()
        val bridgeJob = launch {
            observePendingRestoreResultLifecycle(
                lifecycle = owner.lifecycle,
                onStarted = { callbacks += "start" },
                onStopped = { callbacks += "stop" },
            )
        }
        advanceUntilIdle()

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        advanceUntilIdle()
        assertEquals(listOf("start"), callbacks)

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        advanceUntilIdle()
        assertEquals(listOf("start", "stop"), callbacks)

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        advanceUntilIdle()
        assertEquals(listOf("start", "stop", "start"), callbacks)

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        bridgeJob.cancel()
        advanceUntilIdle()
        assertEquals(listOf("start", "stop", "start", "stop"), callbacks)
    }

    /** controlled lifecycleをrepeatOnLifecycleへ渡すためのtest owner。 */
    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    }
}
