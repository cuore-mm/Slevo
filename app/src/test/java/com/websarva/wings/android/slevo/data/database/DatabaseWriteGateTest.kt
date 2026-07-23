package com.websarva.wings.android.slevo.data.database

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DatabaseWriteGate] の並行制御ロジックを検証する JVM unit test。
 *
 * 設計目標 (design.md / tasks.md 3.1〜3.7) を満たすことを決定的に確認する。
 * 検証には `UnconfinedTestDispatcher` を使い、launch 直後に処理を進める。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseWriteGateTest {
    // --- 3.1: 通常時の withWritePermit は待機せず実行される / 複数 withWritePermit は直列化されない ---

    @Test
    fun withWritePermit_runsImmediatelyWhenNoSuspension() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val ran = CompletableDeferred<Int>()

        val job = launch {
            gate.withWritePermit {
                ran.complete(1)
            }
        }
        advanceUntilIdle()
        job.join()

        assertEquals(1, ran.await())
    }

    @Test
    fun withWritePermit_doesNotSerializeNormalWrites() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val maxConcurrent = Int.MAX_VALUE
        var current = 0
        var observed = 0

        val jobs = List(4) {
            launch {
                gate.withWritePermit {
                    current += 1
                    observed = maxOf(observed, current)
                    delay(1)
                    current -= 1
                }
            }
        }
        advanceUntilIdle()
        jobs.forEach { it.join() }

        // 通常時は 4 つの writer が並行実行され、観測される最大同時実行数は 1 を超える。
        assertTrue(
            "expected concurrent writers > 1, observed=$observed",
            observed > 1
        )
    }

    // --- 3.2: 停止区間中の新規 withWritePermit は待機し、停止解除後に再開する ---

    @Test
    fun withWritePermit_waitsForActiveSuspension() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val suspendedEntered = CompletableDeferred<Unit>()
        val writerRan = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val suspension = launch {
            gate.withWritesSuspended {
                suspendedEntered.complete(Unit)
                release.await()
            }
        }
        suspendedEntered.await()

        val writer = launch {
            gate.withWritePermit {
                writerRan.complete(Unit)
            }
        }
        advanceUntilIdle()
        assertTrue("writer should not run while suspension is active", !writerRan.isCompleted)

        release.complete(Unit)
        advanceUntilIdle()
        writer.join()
        suspension.join()
        assertTrue(writerRan.isCompleted)
    }

    // --- 3.3: 進行中 writer の完了後に suspension が走り、停止要求後かつ block 開始前の writer も待機する ---

    @Test
    fun withWritesSuspended_waitsForActiveWriterAndBlocksNewWriters() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val writerInside = CompletableDeferred<Unit>()
            val writerRelease = CompletableDeferred<Unit>()
            val suspensionEntered = CompletableDeferred<Unit>()
            val suspensionRelease = CompletableDeferred<Unit>()
            val newWriterRan = CompletableDeferred<Unit>()

            val writer = launch {
                gate.withWritePermit {
                    writerInside.complete(Unit)
                    writerRelease.await()
                }
            }
            writerInside.await()

            val suspension = launch {
                gate.withWritesSuspended {
                    suspensionEntered.complete(Unit)
                    suspensionRelease.await()
                }
            }
            advanceUntilIdle()
            assertTrue(
                "suspension must wait for active writer",
                !suspensionEntered.isCompleted
            )

            // 停止要求後に到着した新規 writer は、suspension が終わるまで実行されない。
            val newWriter = launch {
                gate.withWritePermit {
                    newWriterRan.complete(Unit)
                }
            }
            advanceUntilIdle()
            assertTrue(
                "new writer must wait while suspension is pending",
                !newWriterRan.isCompleted
            )

            // まず active writer を解放し、suspension に入らせる。
            writerRelease.complete(Unit)
            writer.join()
            advanceUntilIdle()
            suspensionEntered.await()
            assertTrue(
                "new writer must still wait while suspension block is running",
                !newWriterRan.isCompleted
            )

            suspensionRelease.complete(Unit)
            suspension.join()
            advanceUntilIdle()
            newWriter.join()
            assertTrue(newWriterRan.isCompleted)
        }

    // --- 3.4: 複数 queued suspension は FIFO / 停止要求後の writer は queued suspension 完了後に再開 ---

    @Test
    fun withWritesSuspended_queuedInFifoOrder_andWaitingWritersRunAfter() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val events = mutableListOf<String>()
            val s1Entered = CompletableDeferred<Unit>()
            val s1Release = CompletableDeferred<Unit>()
            val s2Entered = CompletableDeferred<Unit>()
            val s2Release = CompletableDeferred<Unit>()

            val s1 = launch {
                gate.withWritesSuspended {
                    events += "S1-enter"
                    s1Entered.complete(Unit)
                    s1Release.await()
                    events += "S1-exit"
                }
            }
            s1Entered.await()

            // S1 進行中は gate 閉なので、writer は waitingWriters へ積まれる。
            val writer = launch {
                gate.withWritePermit {
                    events += "W-enter"
                }
            }
            advanceUntilIdle()
            assertTrue("W must wait while S1 active", !events.contains("W-enter"))

            // S2 も pending queue へ積まれる（FIFO）。
            val s2 = launch {
                gate.withWritesSuspended {
                    events += "S2-enter"
                    s2Entered.complete(Unit)
                    s2Release.await()
                    events += "S2-exit"
                }
            }
            advanceUntilIdle()
            assertTrue("S2 must be pending while S1 active", !s2Entered.isCompleted)

            // S1 を解放 → S2 へ進む（gate は閉じたまま）。
            s1Release.complete(Unit)
            s1.join()
            advanceUntilIdle()
            s2Entered.await()

            // S2 中はやはり gate 閉なので、W2 も waitingWriters へ。
            val writer2 = launch {
                gate.withWritePermit {
                    events += "W2-enter"
                }
            }
            advanceUntilIdle()
            assertTrue("W2 must wait while S2 active", !events.contains("W2-enter"))

            // S2 を解放 → gate 開 → W, W2 が順次再開。
            s2Release.complete(Unit)
            s2.join()
            advanceUntilIdle()
            writer.join()
            writer2.join()

            // イベント順:
            // S1 開始 → S1 終了 → S2 開始 (FIFO) → S2 終了 → W 再開 → W2 再開
            val s1EnterIdx = events.indexOf("S1-enter")
            val s1ExitIdx = events.indexOf("S1-exit")
            val s2EnterIdx = events.indexOf("S2-enter")
            val s2ExitIdx = events.indexOf("S2-exit")
            val wEnterIdx = events.indexOf("W-enter")
            val w2EnterIdx = events.indexOf("W2-enter")
            assertTrue("S1 enters before S1 exits", s1EnterIdx < s1ExitIdx)
            assertTrue("S1 exits before S2 enters", s1ExitIdx < s2EnterIdx)
            assertTrue("S2 enters before S2 exits", s2EnterIdx < s2ExitIdx)
            assertTrue("S2 exits before W enters", s2ExitIdx < wEnterIdx)
            assertTrue("W enters before W2 enters", wEnterIdx < w2EnterIdx)
        }

    // --- 3.5: 待機中の writer より後に到着した suspension は待機 writer を追い越さない ---

    @Test
    fun withWritesSuspended_doesNotPreemptWaitingWriter() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val events = mutableListOf<String>()
            val s1Entered = CompletableDeferred<Unit>()
            val s1Release = CompletableDeferred<Unit>()
            val writerRelease = CompletableDeferred<Unit>()
            val s3Entered = CompletableDeferred<Unit>()
            val s3Release = CompletableDeferred<Unit>()

            // --- S1 開始（gate 閉）---
            val s1 = launch {
                gate.withWritesSuspended {
                    events += "S1-enter"
                    s1Entered.complete(Unit)
                    s1Release.await()
                    events += "S1-exit"
                }
            }
            s1Entered.await()

            // --- W 投入 → waitingWriters へ ---
            val writer = launch {
                gate.withWritePermit {
                    events += "W-enter"
                    // writer が走り始めたら writerRelease まで待つ。
                    writerRelease.await()
                    events += "W-exit"
                }
            }
            advanceUntilIdle()
            assertTrue("W must wait while S1 active", !events.contains("W-enter"))

            // --- S2 投入 → pending queue へ（FIFO）---
            val s2 = launch {
                gate.withWritesSuspended {
                    events += "S2-enter"
                }
            }
            advanceUntilIdle()
            assertTrue("S2 must be pending while S1 active", !events.contains("S2-enter"))

            // --- S1 解放 → S2 へ ---
            s1Release.complete(Unit)
            s1.join()
            advanceUntilIdle()
            s2.join()
            advanceUntilIdle()

            // S2 完了 → gate 開 → W が再開される。
            assertTrue("W should have entered after S2 completed", events.contains("W-enter"))
            // W はまだ writerRelease 待ち（W-exit はまだ）
            assertTrue("W should still be running (waiting for release)", !events.contains("W-exit"))

            // --- S3 投入（W が active なので pending へ）---
            val s3 = launch {
                gate.withWritesSuspended {
                    events += "S3-enter"
                    s3Entered.complete(Unit)
                    s3Release.await()
                    events += "S3-exit"
                }
            }
            advanceUntilIdle()
            // S3 は W が完了するまで待機する（追い越されない）。
            assertTrue("S3 must wait for W to complete", !s3Entered.isCompleted)

            // --- W 解放 → S3 へ ---
            writerRelease.complete(Unit)
            writer.join()
            advanceUntilIdle()
            // W 完了により S3 が進行。
            s3Entered.await()

            // --- S3 解放 ---
            s3Release.complete(Unit)
            s3.join()

            // イベント順検証: W-exit は S3-enter より前。
            val wExitIdx = events.indexOf("W-exit")
            val s3EnterIdx = events.indexOf("S3-enter")
            assertTrue("W must complete before S3 enters", wExitIdx in 0 until s3EnterIdx)
        }

    // --- 3.6: block の例外/キャンセル後に gate 状態が復旧する ---

    @Test
    fun gate_recoversAfterBlockException() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val writerEntered = CompletableDeferred<Unit>()
        val writerRelease = CompletableDeferred<Unit>()
        val suspensionCompleted = CompletableDeferred<Unit>()

        // --- 進行中 writer ---
        val writer: Job = launch {
            gate.withWritePermit {
                writerEntered.complete(Unit)
                writerRelease.await()
            }
        }
        writerEntered.await()

        // --- suspension を要求（待機）---
        val s1: Job = launch {
            gate.withWritesSuspended {
                suspensionCompleted.complete(Unit)
            }
        }
        advanceUntilIdle()
        assertTrue("s1 must be pending while writer is active", !suspensionCompleted.isCompleted)

        // --- writer を例外で強制終了 ---
        // writer.cancel() により CancellationException が発生し、
        // withWritePermit の finally で activeWriters が減算され suspension queue が進行する。
        writer.cancel()
        writer.join()
        advanceUntilIdle()
        s1.join()
        // suspension が正常に完了している。
        assertTrue("suspension should complete after writer cancelled", suspensionCompleted.isCompleted)

        // --- gate が復旧し後続書き込みが走れる ---
        val after = CompletableDeferred<Unit>()
        val job = launch {
            gate.withWritePermit { after.complete(Unit) }
        }
        advanceUntilIdle()
        job.join()
        assertTrue("subsequent writer should run after recovery", after.isCompleted)
    }

    // --- 3.7: withWritePermit 入場待ち / active writer drain 待ち / 先行 suspension 待ち / suspension 中のキャンセル ---

    @Test
    fun withWritePermit_cancelWhileWaitingRecovers() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val writerEntered = CompletableDeferred<Unit>()
            val writerRelease = CompletableDeferred<Unit>()
            val suspensionCompleted = CompletableDeferred<Unit>()

            // --- 進行中 writer ---
            val activeWriter = launch {
                gate.withWritePermit {
                    writerEntered.complete(Unit)
                    writerRelease.await()
                }
            }
            writerEntered.await()

            // --- suspension を要求（pending へ）---
            val suspension = launch {
                gate.withWritesSuspended {
                    suspensionCompleted.complete(Unit)
                }
            }
            advanceUntilIdle()
            assertTrue("suspension must be pending", !suspensionCompleted.isCompleted)

            // --- 待機 writer を起動（waitingWriters へ）---
            val waitingWriter = launch {
                gate.withWritePermit { /* 正常に終了可能 */ }
            }
            advanceUntilIdle()
            assertTrue("waitingWriter must be pending", !waitingWriter.isCompleted)

            // --- 待機 writer をキャンセル。gate 状態は壊れない ---
            waitingWriter.cancelAndJoin()

            // --- active writer を解放 → suspension 進行 ---
            writerRelease.complete(Unit)
            activeWriter.join()
            advanceUntilIdle()
            suspension.join()
            assertTrue("suspension should complete", suspensionCompleted.isCompleted)

            // --- gate 復旧後、後続 writer が走れる ---
            val after = CompletableDeferred<Unit>()
            val job = launch {
                gate.withWritePermit { after.complete(Unit) }
            }
            advanceUntilIdle()
            job.join()
            assertTrue("subsequent writer should run after recovery", after.isCompleted)
        }
}
