package com.websarva.wings.android.slevo.data.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room DB 書き込みを直列化または停止する共通ゲート。
 *
 * 役割:
 * - 通常の Room DB 書き込み経路を `withWritePermit { ... }` で囲み、停止区間中なら
 *   block 開始前まで待機させる。
 * - バックアップなどの排他処理が `withWritesSuspended { ... }` で新規書き込みを待機させ、
 *   進行中の `withWritePermit` が完了してから block を実行できるようにする。
 *
 * 並行制御の方針:
 * - 通常時の `withWritePermit` 同士は gate 側で直列化しない。Room/SQLite 側の
 *   transaction や DAO レベルの待機はそちらに委ねる。
 * - `withWritesSuspended` は要求順に FIFO で実行する。同時並行はしない。
 * - 待機中の `withWritePermit` は、後続の `withWritesSuspended` に追い越されない。
 * - block の例外/キャンセル時も gate 状態（active writer 数、closed 状態、
 *   pending suspension、waiting writer）が必ず復旧する。
 *
 * 複数 Repository/DataSource をまたぐ書き込みで二重 gate が発生しないよう、
 * 外側 orchestration だけが `withWritePermit` を取得し、内側で呼ばれる書き込みは
 * private/internal の `*Ungated` helper に分離する。
 */
@Singleton
class DatabaseWriteGate @Inject constructor() {
    /**
     * gate の内部状態。
     *
     * @property activeWriters 現在 `withWritePermit` block を実行中の writer 数。
     * @property closed 停止要求が立っているか。`true` の間は新規 `withWritePermit` は待機する。
     *   停止要求の段階から立つ（active writer 待機中の queued suspension を含む）。
     * @property pendingSuspensions 待機中の `withWritesSuspended` の待機 Deferred (FIFO)。
     * @property waitingWriters closed 期間中に待機している `withWritePermit` の待機 Deferred (FIFO)。
     */
    private data class State(
        val activeWriters: Int = 0,
        val closed: Boolean = false,
        val pendingSuspensions: List<CompletableDeferred<Unit>> = emptyList(),
        val waitingWriters: List<CompletableDeferred<Unit>> = emptyList(),
    )

    private val stateRef = java.util.concurrent.atomic.AtomicReference(State())
    private val stateLock = Mutex()

    /**
     * 通常の Room DB 書き込みを実行する。
     *
     * 振る舞い:
     * - 停止要求がない場合は待機せず block を実行する。
     * - 停止区間中、または停止要求後に到着した場合は、gate が再び開くまで待機する。
     * - 待機中の `withWritePermit` は、後続の `withWritesSuspended` に追い越されない。
     *
     * block が例外を投げた場合でも active writer 数は必ず減算される。
     */
    suspend fun <T> withWritePermit(block: suspend () -> T): T = coroutineScope {
        // --- 入場 ---
        val waitForClosed: CompletableDeferred<Unit>? = stateLock.withLock {
            val current = stateRef.get()
            if (!current.closed) {
                // 入場可。active writer を増やしてそのまま block へ。
                stateRef.set(current.copy(activeWriters = current.activeWriters + 1))
                null
            } else {
                // 停止区間中。waitingWrites へ追加。
                val waiter = CompletableDeferred<Unit>()
                stateRef.set(
                    current.copy(
                        waitingWriters = current.waitingWriters + waiter
                    )
                )
                waiter
            }
        }
        if (waitForClosed != null) {
            // キャンセル対応のため、await 中にキャンセルされたら
            // waitingWriters から自分を取り除く。
            try {
                waitForClosed.await()
            } catch (ce: CancellationException) {
                cleanupWriterWaiter(waitForClosed)
                throw ce
            }
            // 待機解除後、active writer を 1 増やす。
            stateLock.withLock {
                val current = stateRef.get()
                stateRef.set(current.copy(activeWriters = current.activeWriters + 1))
            }
        }
        try {
            block()
        } finally {
            // --- 退出 ---
            // active writer を減らし、0 なら次の suspension を起こす。
            stateLock.withLock {
                val current = stateRef.get()
                val newActive = current.activeWriters - 1
                if (newActive < 0) {
                    // 不整合。安全のため 0 に戻す。
                    stateRef.set(current.copy(activeWriters = 0))
                } else {
                    stateRef.set(current.copy(activeWriters = newActive))
                }
                if (newActive == 0) {
                    advanceSuspensionQueueLocked()
                }
            }
        }
    }

    /**
     * バックアップなどの排他処理を実行する。
     *
     * 振る舞い:
     * - active writer が 0 で gate が開いている場合は即時 closed にして block を実行する。
     * - そうでない場合は pending suspension キューへ FIFO で追加する。
     *   停止要求は要求時点で立つため、closed はこの時点で true になる。
     * - 待機中の `withWritePermit` は、停止要求後に到着したものも含めて停止解除まで待たされる。
     * - block 完了後に次の suspension を起こすか、最後なら gate を開いて waiting writer を
     *   順次再開する。
     * - block が例外/キャンセルで終了しても gate 状態は復旧する。
     */
    suspend fun <T> withWritesSuspended(block: suspend () -> T): T = coroutineScope {
        // --- 入場 ---
        // 自分が gate を即時開始するかどうかを決める。
        val (startedImmediately, pendingWaiter) = stateLock.withLock {
            val current = stateRef.get()
            if (current.activeWriters == 0 && !current.closed) {
                // 即時開始。closed = true にして block へ。
                stateRef.set(current.copy(closed = true))
                true to null
            } else {
                // 待機。pending suspension キューへ追加し、停止要求を立てる。
                val waiter = CompletableDeferred<Unit>()
                stateRef.set(
                    current.copy(
                        closed = true,
                        pendingSuspensions = current.pendingSuspensions + waiter
                    )
                )
                false to waiter
            }
        }
        if (!startedImmediately) {
            if (pendingWaiter == null) {
                // 論理上到達しない。
                error("DatabaseWriteGate: pendingWaiter must not be null when not started immediately")
            }
            try {
                pendingWaiter.await()
            } catch (ce: CancellationException) {
                cleanupSuspensionWaiter(pendingWaiter)
                throw ce
            }
        }
        // block 実行。完了（成功/失敗/キャンセル）後、必ず gate を次の状態へ進める。
        try {
            block()
        } finally {
            // --- 退出 ---
            // 次の状態を決める。
            stateLock.withLock {
                if (stateRef.get().pendingSuspensions.isNotEmpty()) {
                    // 次の suspension を起こす。closed のまま。
                    advanceSuspensionQueueLocked()
                } else {
                    // 全 suspension 完了。gate を開く。
                    stateRef.set(stateRef.get().copy(closed = false))
                    // 待機中の writer を順次再開する。
                    resumeWaitingWritersLocked()
                }
            }
        }
    }

    /**
     * 待機中の writer を順次再開する。
     *
     * waitingWriters は到着順 FIFO で再開する。
     */
    private fun resumeWaitingWritersLocked() {
        val current = stateRef.get()
        val waiting = current.waitingWriters
        if (waiting.isEmpty()) {
            return
        }
        // 全員を待ち行列から外し、順次 resume する。
        stateRef.set(current.copy(waitingWriters = emptyList()))
        waiting.forEach { it.complete(Unit) }
    }

    /**
     * 次の pending suspension を起こす。gate は閉じたまま。
     */
    private fun advanceSuspensionQueueLocked() {
        val current = stateRef.get()
        if (current.pendingSuspensions.isEmpty()) {
            return
        }
        val next = current.pendingSuspensions.first()
        val rest = current.pendingSuspensions.drop(1)
        stateRef.set(current.copy(pendingSuspensions = rest))
        next.complete(Unit)
    }

    /**
     * writer の待機オブジェクトがキャンセルされた場合に waitingWriters から取り除く。
     */
    private suspend fun cleanupWriterWaiter(waiter: CompletableDeferred<Unit>) {
        stateLock.withLock {
            val current = stateRef.get()
            if (current.waitingWriters.contains(waiter)) {
                stateRef.set(
                    current.copy(
                        waitingWriters = current.waitingWriters.filterNot { it === waiter }
                    )
                )
            }
            // 待機 writer が居なくなった結果、suspension を進行できるケースがある。
            tryAdvanceBecauseQueueChanged()
        }
    }

    /**
     * suspension の待機オブジェクトがキャンセルされた場合に pendingSuspensions から取り除く。
     */
    private suspend fun cleanupSuspensionWaiter(waiter: CompletableDeferred<Unit>) {
        stateLock.withLock {
            val current = stateRef.get()
            if (current.pendingSuspensions.contains(waiter)) {
                stateRef.set(
                    current.copy(
                        pendingSuspensions = current.pendingSuspensions.filterNot { it === waiter }
                    )
                )
            }
            tryAdvanceBecauseQueueChanged()
        }
    }

    /**
     * 待機列の変動によって gate 状態を前進させられるかを再評価する。
     *
     * トリガ:
     * - waiting writer がキャンセルされ、active writer が 0 かつ closed のとき
     *   次の suspension を進行できる。
     * - pending suspension がキャンセルされ、active writer が 0 かつ closed のとき
     *   gate を開放できる。
     */
    private fun tryAdvanceBecauseQueueChanged() {
        val current = stateRef.get()
        if (current.activeWriters != 0 || !current.closed) {
            return
        }
        if (current.pendingSuspensions.isNotEmpty()) {
            advanceSuspensionQueueLocked()
        } else {
            stateRef.set(current.copy(closed = false))
            resumeWaitingWritersLocked()
        }
    }
}
