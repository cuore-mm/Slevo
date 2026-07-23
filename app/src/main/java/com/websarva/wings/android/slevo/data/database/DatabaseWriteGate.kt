package com.websarva.wings.android.slevo.data.database

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room DB 書き込みの排他制御を行う共通 gate。
 *
 * 単一 FIFO queue と active writer 予約により、writer 再開直後の race を防ぎつつ
 * `withWritesSuspended` の排他性を保証する。
 *
 * - 通常 writer は `suspensionActive == false` かつ queue が空の場合に即時入場する。
 * - `withWritesSuspended` は `activeWriters == 0 && !suspensionActive && queue.isEmpty()`
 *   の場合に即時開始する。
 * - writer / suspension は到着順で queue 管理し、古い writer group を後続 suspension が
 *   追い越さない。
 * - queue から writer を再開する前に `activeWriters` を予約し、後続 suspension の割り込みを防ぐ。
 * - block の例外・cancellation 時に gate 状態が必ず復旧する。
 * - block の戻り値・例外・cancellation は cleanup 後にそのまま呼び出し元へ伝播する。
 *
 * 複数 Repository/DataSource をまたぐ書き込みで二重 gate が発生しないよう、
 * 外側 orchestration だけが `withWritePermit` を取得し、内側で呼ばれる書き込みは
 * private/internal の `*Ungated` helper に分離する。
 */
@Singleton
class DatabaseWriteGate @Inject constructor() {

    // --- Waiter types ---

    /**
     * gate 内の待機要素を表す sealed interface。
     *
     * [WriterWaiter] は `withWritePermit` の、[SuspensionWaiter] は `withWritesSuspended` の
     * 待機を表現する。
     */
    private sealed interface Waiter {
        val signal: CompletableDeferred<Unit>
    }

    /**
     * `withWritePermit` の writer 待機状態。
     *
     * lifecycle: `QUEUED` → `RESERVED` → `RUNNING` → `RELEASED`
     */
    private enum class WriterWaiterState {
        /** queue 内で待機中。 */
        QUEUED,
        /**
         * queue から取り出され、[activeWriters] に予約済み。
         * [signal.await] が成功する前の状態。
         */
        RESERVED,
        /** [signal.await] が成功し、[block] を実行中。 */
        RUNNING,
        /** release 済み。[activeWriters] を減算済み。 */
        RELEASED,
    }

    /**
     * `withWritePermit` の waiter。
     * [state] により lifecycle を管理する。
     */
    private data class WriterWaiter(
        override val signal: CompletableDeferred<Unit>,
        var state: WriterWaiterState = WriterWaiterState.QUEUED,
    ) : Waiter

    /**
     * `withWritesSuspended` の suspension 待機状態。
     *
     * lifecycle: `QUEUED` → `ACTIVE` → `RELEASED`
     */
    private enum class SuspensionWaiterState {
        /** queue 内で待機中。 */
        QUEUED,
        /** queue から取り出され、[suspensionActive] が true の状態。 */
        ACTIVE,
        /** release 済み。[suspensionActive] を戻し queue 前進済み。 */
        RELEASED,
    }

    /**
     * `withWritesSuspended` の waiter。
     * [state] により lifecycle を管理する。
     */
    private data class SuspensionWaiter(
        override val signal: CompletableDeferred<Unit>,
        var state: SuspensionWaiterState = SuspensionWaiterState.QUEUED,
    ) : Waiter

    // --- State ---

    /**
     * gate の内部状態。
     *
     * @property activeWriters 現在 block を実行中または予約済みの writer 数。
     * @property suspensionActive 現在 `withWritesSuspended` の block を実行中か。
     * @property queue writer と suspension の待機 FIFO queue。
     */
    private data class State(
        val activeWriters: Int = 0,
        val suspensionActive: Boolean = false,
        val queue: List<Waiter> = emptyList(),
    )

    private val stateRef = java.util.concurrent.atomic.AtomicReference(State())
    private val stateLock = Mutex()

    /**
     * RESERVED signal受信後、state lock取得前に実行するtest hook。
     *
     * productionでは何もしない。JVM testだけがこのwindowでcoroutineを停止し、
     * cancellation後のreservation cleanupを検証するために差し替える。
     */
    internal var afterWriterSignalHook: suspend () -> Unit = {}

    /**
     * state lockをtestから保持するためのinternal seam。
     *
     * production APIでは使用せず、test以外ではstate lock内でsuspendしないこと。
     */
    internal suspend fun <T> withStateLockHeldForTest(block: suspend () -> T): T =
        stateLock.withLock { block() }

    // --- Public API ---

    /**
     * 通常の Room DB 書き込みを実行する。
     *
     * `suspensionActive == false` かつ queue が空の場合は即時入場する。
     * それ以外の場合は queue 末尾で待機し、再開時に予約された active writer として block を実行する。
     *
     * @param block 実行する書き込み処理。
     * @return block の戻り値。例外/cancellation は cleanup 後にそのまま伝播する。
     */
    suspend fun <T> withWritePermit(block: suspend () -> T): T {
        // --- 入場 ---
        val runningWriter: WriterWaiter = stateLock.withLock {
            val current = stateRef.get()
            if (!current.suspensionActive && current.queue.isEmpty()) {
                // 即時入場。active token を作成して activeWriters を増やす。
                val writer = WriterWaiter(
                    signal = CompletableDeferred(),
                    state = WriterWaiterState.RUNNING,
                )
                stateRef.set(current.copy(activeWriters = current.activeWriters + 1))
                writer
            } else {
                // queue 末尾で待機。
                val writer = WriterWaiter(signal = CompletableDeferred())
                stateRef.set(current.copy(queue = current.queue + writer))
                writer
            }
        }

        return try {
            if (runningWriter.state == WriterWaiterState.QUEUED) {
                // --- Queue wait ---
                runningWriter.signal.await()
                afterWriterSignalHook()

                // --- RESERVED -> RUNNING ---
                withNonCancellableStateLock {
                    if (runningWriter.state == WriterWaiterState.RESERVED) {
                        runningWriter.state = WriterWaiterState.RUNNING
                    }
                }
                // signal後にcancelされたwriterはuser blockを開始しない。
                currentCoroutineContext().ensureActive()
            }

            // --- User block ---
            block()
        } finally {
            // token解放はcaller cancellation後も必ず完了させる。
            cleanupWriter(runningWriter)
        }
    }

    /**
     * バックアップなどの排他処理を実行する。
     *
     * `activeWriters == 0 && !suspensionActive && queue.isEmpty()` の場合は即時開始する。
     * それ以外の場合は queue 末尾で待機し、再開時に active suspension として block を実行する。
     *
     * @param block 実行する排他処理。
     * @return block の戻り値。例外/cancellation は cleanup 後にそのまま伝播する。
     */
    suspend fun <T> withWritesSuspended(block: suspend () -> T): T {
        // --- 入場 ---
        val activeSuspension: SuspensionWaiter = stateLock.withLock {
            val current = stateRef.get()
            if (current.activeWriters == 0 && !current.suspensionActive && current.queue.isEmpty()) {
                // 即時開始。active token を作成。
                val suspension = SuspensionWaiter(
                    signal = CompletableDeferred(),
                    state = SuspensionWaiterState.ACTIVE,
                )
                stateRef.set(current.copy(suspensionActive = true))
                suspension
            } else {
                // queue 末尾で待機。
                val suspension = SuspensionWaiter(signal = CompletableDeferred())
                stateRef.set(current.copy(queue = current.queue + suspension))
                suspension
            }
        }

        return try {
            if (activeSuspension.state == SuspensionWaiterState.QUEUED) {
                // --- Queue wait ---
                activeSuspension.signal.await()
            }

            // --- User block ---
            block()
        } finally {
            // suspension解放はcaller cancellation後も必ず完了させる。
            cleanupSuspension(activeSuspension)
        }
    }

    // --- queue advancement ---

    /**
     * queue 先頭から次の実行対象を予約・開始する。
     *
     * `suspensionActive == true` または `activeWriters > 0` の場合は何もしない。
     * queue が空の場合も何もしない。
     *
     * - queue 先頭が [SuspensionWaiter] の場合は 1 件だけ ACTIVE にして進める。
     * - queue 先頭が [WriterWaiter] の場合は次の [SuspensionWaiter] までの連続 writer 群を
     *   まとめて RESERVED にし、`activeWriters` を予約してから resume 可能にする。
     *
     * `stateLock` 内で呼び出すこと。
     */
    private fun advanceQueueLocked() {
        val current = stateRef.get()
        if (current.suspensionActive || current.activeWriters > 0) return
        if (current.queue.isEmpty()) return

        val first = current.queue.first()
        when (first) {
            is SuspensionWaiter -> {
                // 1 件だけ開始。
                first.state = SuspensionWaiterState.ACTIVE
                stateRef.set(
                    current.copy(
                        suspensionActive = true,
                        queue = current.queue.drop(1),
                    ),
                )
                // reservation が完了したので signal を起こす。
                // complete は lock 内でも良いし、本実装では lock 内で行う。
                first.signal.complete(Unit)
            }
            is WriterWaiter -> {
                // 次の suspension までの連続 writer をまとめて予約。
                val writers = current.queue.takeWhile { it is WriterWaiter }
                val remaining = current.queue.drop(writers.size)
                writers.forEach { (it as WriterWaiter).state = WriterWaiterState.RESERVED }
                val signals = writers.map { it.signal }
                stateRef.set(
                    current.copy(
                        activeWriters = current.activeWriters + writers.size,
                        queue = remaining,
                    ),
                )
                // reservation 完了後に signal を起こす。
                signals.forEach { it.complete(Unit) }
            }
        }
    }

    // --- release helpers ---

    /**
     * 予約済み writer の active writer 予約を解放する。
     *
     * `RESERVED` または `RUNNING` の writer を `RELEASED` に遷移させ、
     * `activeWriters` を 1 減らす。すでに `RELEASED` の場合は何もしない。
     *
     * `activeWriters` が 0 になった場合は `advanceQueueLocked()` を呼び、
     * 後続の suspension または writer 群を進める。
     *
     * `stateLock` 内で呼び出すこと。
     */
    private fun releaseReservedWriterLocked(writer: WriterWaiter) {
        if (writer.state == WriterWaiterState.RELEASED) return
        if (writer.state == WriterWaiterState.RESERVED || writer.state == WriterWaiterState.RUNNING) {
            writer.state = WriterWaiterState.RELEASED
            val current = stateRef.get()
            val newActive = current.activeWriters - 1
            stateRef.set(current.copy(activeWriters = newActive.coerceAtLeast(0)))
            if (newActive <= 0) {
                advanceQueueLocked()
            }
        }
    }

    /**
     * active suspension の状態を解放する。
     *
     * `ACTIVE` の suspension を `RELEASED` に遷移させ、`suspensionActive = false` に戻し、
     * queue 前進を行う。すでに `RELEASED` の場合は何もしない。
     *
     * `stateLock` 内で呼び出すこと。
     */
    private fun releaseActiveSuspensionLocked(suspension: SuspensionWaiter) {
        if (suspension.state == SuspensionWaiterState.RELEASED) return
        if (suspension.state == SuspensionWaiterState.ACTIVE) {
            suspension.state = SuspensionWaiterState.RELEASED
            stateRef.set(stateRef.get().copy(suspensionActive = false))
            advanceQueueLocked()
        }
    }

    // --- cancellation cleanup ---

    /**
     * 待機中または予約済みの writer の cancellation cleanup を行う。
     *
     * - queue に残っている writer は queue から取り除き、queue が idle なら前進させる。
     * - queue から取り出され予約済み (RESERVED) の場合は [releaseReservedWriterLocked] で解放する。
     *   queue 前進は release helper の責務とする。
     *
     * `stateLock` 内で呼び出すこと。
     */
    private suspend fun cleanupWriter(writer: WriterWaiter) {
        withNonCancellableStateLock {
            if (writer.state == WriterWaiterState.QUEUED) {
                // まだ queue 内 → 取り除く。
                val current = stateRef.get()
                val removed = current.queue.filterNot { it === writer }
                stateRef.set(current.copy(queue = removed))
                // gate が idle かつ queue に後続があれば前進。
                if (!current.suspensionActive && current.activeWriters == 0) {
                    advanceQueueLocked()
                }
            } else {
                // 予約済み → release path へ。
                releaseReservedWriterLocked(writer)
            }
        }
    }

    /**
     * 待機中または active 化済みの suspension の cancellation cleanup を行う。
     *
     * - queue に残っている suspension は queue から取り除き、queue が idle なら前進させる。
     * - ACTIVE 化済みの場合は [releaseActiveSuspensionLocked] で解放する。
     *   queue 前進は release helper の責務とする。
     *
     * `stateLock` 内で呼び出すこと。
     */
    private suspend fun cleanupSuspension(suspension: SuspensionWaiter) {
        withNonCancellableStateLock {
            if (suspension.state == SuspensionWaiterState.QUEUED) {
                // まだ queue 内 → 取り除く。
                val current = stateRef.get()
                val removed = current.queue.filterNot { it === suspension }
                stateRef.set(current.copy(queue = removed))
                // suspension が取り除かれた結果、queue に writer が残っている場合、
                // activeWriters が 0 で suspensionActive でなければ前進させる。
                if (!current.suspensionActive && current.activeWriters == 0) {
                    advanceQueueLocked()
                }
            } else {
                // ACTIVE 化済み → release path へ。
                releaseActiveSuspensionLocked(suspension)
            }
        }
    }

    /**
     * cancellation中もgate state cleanupを完了するlock境界。
     *
     * actionは同期的なstate mutationだけを受け取り、user block、signal待機、DB I/O、
     * 外部callbackをこのNonCancellable範囲へ含めない。
     */
    private suspend fun <T> withNonCancellableStateLock(action: () -> T): T =
        withContext(NonCancellable) {
            stateLock.withLock { action() }
        }
}
