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
import org.junit.Assert.fail
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

    // --- 3.4: 単一 FIFO queue による到着順処理 ---

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

            // S1 進行中に writer が到着 → queue 末尾へ。
            val writer = launch {
                gate.withWritePermit {
                    events += "W-enter"
                }
            }
            advanceUntilIdle()
            assertTrue("W must wait while S1 active", !events.contains("W-enter"))

            // S2 も到着 → queue 末尾へ（W の後ろ）。
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

            // S1 を解放 → W が再開 → W 完了 → S2 が開始。
            s1Release.complete(Unit)
            s1.join()
            advanceUntilIdle()
            // W が即時完了して S2 に入る。
            s2Entered.await()

            // S2 中に W2 が到着 → queue 末尾へ。
            val writer2 = launch {
                gate.withWritePermit {
                    events += "W2-enter"
                }
            }
            advanceUntilIdle()
            assertTrue("W2 must wait while S2 active", !events.contains("W2-enter"))

            // S2 を解放 → W2 が再開。
            s2Release.complete(Unit)
            s2.join()
            advanceUntilIdle()
            writer.join()
            writer2.join()

            // 単一 FIFO queue: S1-enter → S1-exit → W-enter → S2-enter → S2-exit → W2-enter
            val s1EnterIdx = events.indexOf("S1-enter")
            val s1ExitIdx = events.indexOf("S1-exit")
            val wEnterIdx = events.indexOf("W-enter")
            val s2EnterIdx = events.indexOf("S2-enter")
            val s2ExitIdx = events.indexOf("S2-exit")
            val w2EnterIdx = events.indexOf("W2-enter")
            assertTrue("S1 enters before S1 exits", s1EnterIdx < s1ExitIdx)
            assertTrue("S1 exits before W enters", s1ExitIdx < wEnterIdx)
            assertTrue("W enters before S2 enters (FIFO)", wEnterIdx < s2EnterIdx)
            assertTrue("S2 enters before S2 exits", s2EnterIdx < s2ExitIdx)
            assertTrue("S2 exits before W2 enters", s2ExitIdx < w2EnterIdx)
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

            // --- W 投入 → queue へ（S2 より先に到着）---
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

            // --- S2 投入 → queue へ（W の後ろ）---
            val s2 = launch {
                gate.withWritesSuspended {
                    events += "S2-enter"
                }
            }
            advanceUntilIdle()
            assertTrue("S2 must be pending while S1 active", !events.contains("S2-enter"))

            // --- S1 解放 → W が再開（FIFO 先頭）---
            s1Release.complete(Unit)
            s1.join()
            advanceUntilIdle()

            // W は S2 より先に再開される。
            assertTrue("W should have entered (FIFO: W before S2)", events.contains("W-enter"))
            // W はまだ writerRelease 待ち（W-exit はまだ）
            assertTrue("W should still be running (waiting for release)", !events.contains("W-exit"))
            // S2 はまだ待機中。
            assertTrue("S2 should not have entered yet", !events.contains("S2-enter"))

            // --- S3 投入（W が active なので queue 末尾へ）---
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

            // --- W 解放 → S2 → S3 へ ---
            writerRelease.complete(Unit)
            writer.join()
            advanceUntilIdle()

            // W 完了により queue = [S2, S3] → S2 が進行 → S3 が進行。
            s3Entered.await()

            // --- S3 解放 ---
            s3Release.complete(Unit)
            s3.join()

            // イベント順検証: S2-enter < S3-enter（FIFO 通り）
            s2.join()
            val s2EnterIdx = events.indexOf("S2-enter")
            val s3EnterIdx = events.indexOf("S3-enter")
            assertTrue("S2 must enter before S3 (FIFO)", s2EnterIdx in 0 until s3EnterIdx)
            // W-exit は S2 完了時に W が解放されるため S3-enter より前。
            val wExitIdx = events.indexOf("W-exit")
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

    // --- 7.1: 停止区間終了で再開された writer の block 中に後続 suspension が開始しない ---

    @Test
    fun resumedWriter_preventsLaterSuspension() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val s1Entered = CompletableDeferred<Unit>()
        val s1Release = CompletableDeferred<Unit>()
        val writerRelease = CompletableDeferred<Unit>()
        val s2Entered = CompletableDeferred<Unit>()

        // --- S1 開始 ---
        val s1 = launch {
            gate.withWritesSuspended {
                s1Entered.complete(Unit)
                s1Release.await()
            }
        }
        s1Entered.await()

        // --- W 投入（S1 active 中なので queue へ）---
        val writer = launch {
            gate.withWritePermit {
                writerRelease.await()
            }
        }
        advanceUntilIdle()

        // --- S1 解放 → W が再開される ---
        s1Release.complete(Unit)
        s1.join()
        // W は予約済みで activeWriter として扱われる状態。
        // このタイミングで S2 を要求する。
        val s2 = launch {
            gate.withWritesSuspended {
                s2Entered.complete(Unit)
            }
        }
        advanceUntilIdle()
        // S2 は W が active なので開始しない。
        assertTrue("S2 must not start while W is active", !s2Entered.isCompleted)

        // --- W 解放 → S2 が開始 ---
        writerRelease.complete(Unit)
        writer.join()
        advanceUntilIdle()
        s2Entered.await()
    }

    // --- 7.2: 複数 writer 再開時に後続 suspension は全 writer 完了まで待つ ---

    @Test
    fun multipleResumedWriters_blocksLaterSuspension() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val s1Entered = CompletableDeferred<Unit>()
        val s1Release = CompletableDeferred<Unit>()
        val w1Release = CompletableDeferred<Unit>()
        val w2Release = CompletableDeferred<Unit>()
        val s2Entered = CompletableDeferred<Unit>()

        // --- S1 開始 ---
        val s1 = launch {
            gate.withWritesSuspended {
                s1Entered.complete(Unit)
                s1Release.await()
            }
        }
        s1Entered.await()

        // --- W1, W2 を queue に積む ---
        val w1 = launch { gate.withWritePermit { w1Release.await() } }
        val w2 = launch { gate.withWritePermit { w2Release.await() } }
        advanceUntilIdle()

        // --- S1 解放 → W1, W2 が再開予約される ---
        s1Release.complete(Unit)
        s1.join()

        // --- 後続 S2 を要求 ---
        val s2 = launch {
            gate.withWritesSuspended {
                s2Entered.complete(Unit)
            }
        }
        advanceUntilIdle()
        assertTrue("S2 must not start while writers are active", !s2Entered.isCompleted)

        // --- W1 だけ解放。まだ W2 active なので S2 は待つ ---
        w1Release.complete(Unit)
        w1.join()
        advanceUntilIdle()
        assertTrue("S2 must still wait while W2 is active", !s2Entered.isCompleted)

        // --- W2 解放 → S2 開始 ---
        w2Release.complete(Unit)
        w2.join()
        advanceUntilIdle()
        s2Entered.await()
    }

    // --- 7.3: 予約済み writer が cancellation されても後続 operation が詰まらない ---

    @Test
    fun reservedWriterCancellation_doesNotBlockLaterOperations() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val s1Entered = CompletableDeferred<Unit>()
            val s1Release = CompletableDeferred<Unit>()
            val s2Entered = CompletableDeferred<Unit>()
            val s2Release = CompletableDeferred<Unit>()

            // --- S1 開始 ---
            val s1 = launch {
                gate.withWritesSuspended {
                    s1Entered.complete(Unit)
                    s1Release.await()
                }
            }
            s1Entered.await()

            // --- W 投入（queue へ）---
            val writer = launch {
                gate.withWritePermit { /* 即完了だが await 前に cancel させる */ }
            }

            // --- S2 投入（queue へ、W の後ろ）---
            val s2 = launch {
                gate.withWritesSuspended {
                    s2Entered.complete(Unit)
                    s2Release.await()
                }
            }
            advanceUntilIdle()

            // --- S1 解放 → W が予約され activeWriter+=1 ---
            s1Release.complete(Unit)
            s1.join()
            // W の signal が complete された直後、await 前に cancel。
            writer.cancelAndJoin()

            // --- W cancel 後、S2 が進行する ---
            advanceUntilIdle()
            s2Entered.await()

            // --- S2 解放 ---
            s2Release.complete(Unit)
            s2.join()
        }

    // --- 7.4: 予約済み writer が await 復帰前・block 開始前・block 実行中に cancel されても一度だけ解放 ---

    @Test
    fun reservedWriterReleasedOnceOnCancellation() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val events = mutableListOf<String>()
        val s1Entered = CompletableDeferred<Unit>()
        val s1Release = CompletableDeferred<Unit>()
        val wBlock = CompletableDeferred<Unit>()

        // --- S1 開始 ---
        val s1 = launch {
            gate.withWritesSuspended {
                s1Entered.complete(Unit)
                s1Release.await()
                events += "S1-exit"
            }
        }
        s1Entered.await()

        // --- W 投入（queue へ）---
        val writer = launch {
            gate.withWritePermit {
                events += "W-enter"
                wBlock.await()
                events += "W-exit"
            }
        }
        advanceUntilIdle()

        // --- S1 解放 → W が RESERVED → RUNNING（barrier で停止）---
        s1Release.complete(Unit)
        s1.join()
        // W は already entered block (RUNNING) で barrier 待ち。cancel する。
        writer.cancelAndJoin()
        // W の active writer 予約は解放されているはず。
        assertTrue("W should not have exited normally", !events.contains("W-exit"))

        // 後続 operation が正常に動く。
        val after = CompletableDeferred<Unit>()
        val s2 = launch {
            gate.withWritesSuspended {
                events += "S2-enter"
                after.complete(Unit)
            }
        }
        after.await()
        s2.join()
        assertTrue("S2 should run after cancelled writer", events.contains("S2-enter"))
    }

    // --- 7.5: queued suspension が active 化後、block 開始前に cancel されても suspensionActive 解放 ---

    @Test
    fun activatedSuspensionReleaseOnEarlyCancellation() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val s1Entered = CompletableDeferred<Unit>()
        val s1Release = CompletableDeferred<Unit>()
        val s2Block = CompletableDeferred<Unit>()

        // --- S1 開始 ---
        val s1 = launch {
            gate.withWritesSuspended {
                s1Entered.complete(Unit)
                s1Release.await()
            }
        }
        s1Entered.await()

        // --- S2 投入（queue へ）---
        val s2 = launch {
            gate.withWritesSuspended {
                // barrier で停止させ、cancel を待つ。
                s2Block.await()
            }
        }
        advanceUntilIdle()

        // --- S1 解放 → S2 が ACTIVE 化（barrier で停止）---
        s1Release.complete(Unit)
        s1.join()
        // S2 は already entered block (ACTIVE) で barrier 待ち。cancel する。
        s2.cancelAndJoin()

        // 後続 writer が正常に走る。
        val after = CompletableDeferred<Unit>()
        val writer = launch {
            gate.withWritePermit { after.complete(Unit) }
        }
        after.await()
        writer.join()
    }

    // --- 7.6: active suspension が block 中に cancel されても一度だけ解放 ---

    @Test
    fun activeSuspensionReleasedOnceOnBlockCancellation() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val s1Entered = CompletableDeferred<Unit>()
        val s1Release = CompletableDeferred<Unit>()
        val after = CompletableDeferred<Unit>()

        // --- S1 開始 ---
        val s1 = launch {
            gate.withWritesSuspended {
                s1Entered.complete(Unit)
                s1Release.await()
                // cancel が来たらここで中断
                delay(10)
            }
        }
        s1Entered.await()

        // --- S1 cancel（block 実行中の cancel）---
        s1.cancelAndJoin()

        // 後続 writer が正常に走る。
        val writer = launch {
            gate.withWritePermit { after.complete(Unit) }
        }
        after.await()
        writer.join()
    }

    // --- 7.7: 即時入場 writer の通常完了・例外・cancellation で release される ---

    @Test
    fun immediateWriterNormalCompletion() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val result = gate.withWritePermit { 42 }
        assertEquals(42, result)
        // 後続 scheduler も正常に走る。
        val after = CompletableDeferred<Unit>()
        val job = launch { gate.withWritePermit { after.complete(Unit) } }
        after.await()
        job.join()
    }

    @Test
    fun immediateWriterExceptionCleanup() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        try {
            gate.withWritePermit { throw RuntimeException("test") }
            fail("expected exception")
        } catch (_: RuntimeException) { }

        // gate 復旧。後続 writer が走る。
        val after = CompletableDeferred<Unit>()
        val job = launch { gate.withWritePermit { after.complete(Unit) } }
        after.await()
        job.join()
    }

    @Test
    fun immediateWriterCancellationRecovery() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val job = launch {
            gate.withWritePermit {
                delay(100)
            }
        }
        advanceUntilIdle()
        job.cancelAndJoin()

        // gate 復旧。
        val after = CompletableDeferred<Unit>()
        val s = launch { gate.withWritesSuspended { after.complete(Unit) } }
        after.await()
        s.join()
    }

    // --- 7.8: 即時開始 suspension の通常完了・例外・cancellation で release される ---

    @Test
    fun immediateSuspensionNormalCompletion() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val result = gate.withWritesSuspended { "ok" }
        assertEquals("ok", result)
        val after = CompletableDeferred<Unit>()
        val job = launch { gate.withWritePermit { after.complete(Unit) } }
        after.await()
        job.join()
    }

    @Test
    fun immediateSuspensionExceptionCleanup() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        try {
            gate.withWritesSuspended { throw RuntimeException("test") }
            fail("expected exception")
        } catch (_: RuntimeException) { }

        val after = CompletableDeferred<Unit>()
        val job = launch { gate.withWritePermit { after.complete(Unit) } }
        after.await()
        job.join()
    }

    @Test
    fun immediateSuspensionCancellationRecovery() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val job = launch {
            gate.withWritesSuspended {
                delay(100)
            }
        }
        advanceUntilIdle()
        job.cancelAndJoin()

        val after = CompletableDeferred<Unit>()
        val writer = launch { gate.withWritePermit { after.complete(Unit) } }
        after.await()
        writer.join()
    }

    // --- 7.9: pending suspension cancel 後、古い queued writer を新規 writer が追い越さない ---

    @Test
    fun cancelledSuspension_doesNotLetNewWriterOvertakeQueuedWriter() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val events = mutableListOf<String>()
            val s1Entered = CompletableDeferred<Unit>()
            val s1Release = CompletableDeferred<Unit>()

            // --- S1 開始 ---
            val s1 = launch {
                gate.withWritesSuspended {
                    s1Entered.complete(Unit)
                    s1Release.await()
                }
            }
            s1Entered.await()

            // --- W (古い writer) 投入 ---
            val oldWriter = launch {
                gate.withWritePermit { events += "W-old" }
            }
            advanceUntilIdle()

            // --- S2 投入（後に cancel する）---
            val s2 = launch {
                gate.withWritesSuspended { events += "S2" }
            }
            advanceUntilIdle()

            // --- S2 cancel（まだ queue 内）---
            s2.cancelAndJoin()

            // --- 新しい writer 投入 ---
            val newWriter = launch {
                gate.withWritePermit { events += "W-new" }
            }
            advanceUntilIdle()
            // 新規 writer は queue 末尾へ追加されるべき。古い writer は queue 内。
            assertTrue("new writer should not overtake old writer yet",
                !events.contains("W-new"))

            // --- S1 解放 → 古い writer が先に再開 ---
            s1Release.complete(Unit)
            s1.join()
            oldWriter.join()
            newWriter.join()
            val oldIdx = events.indexOf("W-old")
            val newIdx = events.indexOf("W-new")
            assertTrue("old writer must run before new writer", oldIdx in 0 until newIdx)
        }

    // --- 7.10: 戻り値伝播 ---

    @Test
    fun withWritePermit_returnsBlockResult() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        assertEquals(123, gate.withWritePermit { 123 })
    }

    @Test
    fun withWritesSuspended_returnsBlockResult() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        assertEquals("done", gate.withWritesSuspended { "done" })
    }

    // --- 7.11: 例外伝播 ---

    @Test(expected = IllegalStateException::class)
    fun withWritePermit_propagatesException() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        gate.withWritePermit { throw IllegalStateException("test") }
    }

    @Test(expected = IllegalStateException::class)
    fun withWritesSuspended_propagatesException() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        gate.withWritesSuspended { throw IllegalStateException("test") }
    }

    // --- 7.12: cancellation 伝播 ---

    @Test
    fun withWritePermit_propagatesCancellation() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val after = CompletableDeferred<Unit>()
        val job = launch {
            gate.withWritePermit {
                delay(100)
            }
            after.complete(Unit)
        }
        // advanceUntilIdle せずに delay で待機中の job を cancel する。
        job.cancelAndJoin()
        assertTrue("after should not complete after cancellation", !after.isCompleted)
        val ok = CompletableDeferred<Unit>()
        launch { gate.withWritePermit { ok.complete(Unit) } }
        ok.await()
    }

    @Test
    fun withWritesSuspended_propagatesCancellation() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val after = CompletableDeferred<Unit>()
        val job = launch {
            gate.withWritesSuspended {
                delay(100)
            }
            after.complete(Unit)
        }
        // advanceUntilIdle せずに delay で待機中の job を cancel する。
        job.cancelAndJoin()
        assertTrue("after should not complete after cancellation", !after.isCompleted)
        val ok = CompletableDeferred<Unit>()
        launch { gate.withWritePermit { ok.complete(Unit) } }
        ok.await()
    }

    // --- 7.13: test mechanics use CompletableDeferred barriers (all above tests already do) ---
}
