package com.websarva.wings.android.slevo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreNotification
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreNotificationType
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreResultConsumer
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreResultRead
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * アプリ起動後のpending restore通知をActivity単位で保持するViewModel。
 *
 * 未通知notificationをstateとして保持し、Snackbar表示完了後のacknowledge成功までresultを
 * 消費しない。これによりCompose collector開始前のone-shot event消失を防ぐ。
 */
@HiltViewModel
class PendingRestoreResultViewModel @Inject constructor(
    private val resultConsumer: PendingRestoreResultConsumer,
    @Named("pendingRestoreIo") private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PendingRestoreResultUiState())
    val uiState: StateFlow<PendingRestoreResultUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null
    private var observationGeneration = 0L
    private var observationStarted = false

    /**
     * ActivityがSTARTEDになった期間のpending result観察を開始する。
     *
     * 同じ観察が既に動作中の場合は現行jobを再利用し、重複した観察generationを作らない。
     */
    fun startObservation() {
        if (observationStarted && observationJob?.isActive == true) return

        observationStarted = true
        observationGeneration += 1
        val generation = observationGeneration
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            observePendingResult(generation)
        }
    }

    /**
     * ActivityがSTOPPEDになったとき、pending result観察を無効化して停止する。
     *
     * generationを先に進めてからjobをcancelすることで、cancelへ即応しないreadが完了しても
     * 旧観察からstateや現行job参照を更新できないようにする。
     */
    fun stopObservation() {
        observationStarted = false
        observationGeneration += 1
        observationJob?.cancel()
        observationJob = null
    }

    /**
     * Snackbar表示完了後にnotificationをconditional acknowledgeする。
     *
     * token不一致やdelete failureではstateを消去せず、現在のresultを再読して新しい通知を失わない。
     */
    fun acknowledgeResult(token: String) {
        if (_uiState.value.notification?.token != token) return

        viewModelScope.launch {
            val acknowledged = withContext(ioDispatcher) {
                resultConsumer.acknowledge(token)
            }
            if (acknowledged) {
                _uiState.update { state ->
                    if (state.notification?.token == token) {
                        state.copy(notification = null, waitingForCompletion = false)
                    } else {
                        state
                    }
                }
            } else if (observationStarted) {
                // 表示中にresultが更新された場合は、現行lifecycle内で新resultを再評価する。
                startObservation()
            }
        }
    }

    /**
     * ViewModel破棄時に観察generationとjob参照を無効化する。
     */
    override fun onCleared() {
        observationStarted = false
        observationGeneration += 1
        observationJob?.cancel()
        observationJob = null
        super.onCleared()
    }

    // --- Pending result observation ---

    /**
     * 現行generationだけを対象に、terminal outcomeまでpending resultを再評価する。
     *
     * 初回readは即時に行い、Pendingの間だけ200ms開始・2秒上限の指数backoffを使う。
     */
    private suspend fun observePendingResult(generation: Long) {
        var delayMillis = INITIAL_DELAY_MILLIS

        try {
            while (isCurrentGeneration(generation)) {
                val outcome = withContext(ioDispatcher) {
                    resultConsumer.read()
                }
                if (!isCurrentGeneration(generation)) return

                when (outcome) {
                    PendingRestoreResultRead.Absent,
                    PendingRestoreResultRead.Unreadable,
                    -> {
                        publish(generation, outcome)
                        return
                    }

                    is PendingRestoreResultRead.Ready -> {
                        publish(generation, outcome)
                        return
                    }

                    is PendingRestoreResultRead.Pending -> {
                        publish(generation, outcome)
                        delay(delayMillis)
                        delayMillis = minOf(delayMillis * 2, MAX_DELAY_MILLIS)
                    }
                }
            }
        } finally {
            // A stale generation must not clear the job owned by a newer observation.
            if (observationGeneration == generation) {
                observationJob = null
            }
        }
    }

    // --- UiState update ---

    /** 現行generationのread outcomeだけをnotificationまたはcompletion待ちstateへ反映する。 */
    private fun publish(generation: Long, outcome: PendingRestoreResultRead) {
        if (!isCurrentGeneration(generation)) return

        _uiState.update { state ->
            when (outcome) {
                PendingRestoreResultRead.Absent,
                PendingRestoreResultRead.Unreadable,
                -> state.copy(notification = null, waitingForCompletion = false)

                is PendingRestoreResultRead.Pending -> {
                    state.copy(waitingForCompletion = state.notification == null)
                }

                is PendingRestoreResultRead.Ready -> {
                    state.copy(
                        notification = outcome.notification.toUiModel(),
                        waitingForCompletion = false,
                    )
                }
            }
        }
    }

    /**
     * 観察jobが現在stateへ作用できるgenerationかを確認する。
     */
    private fun isCurrentGeneration(generation: Long): Boolean =
        observationStarted && observationGeneration == generation

    private companion object {
        private const val INITIAL_DELAY_MILLIS = 200L
        private const val MAX_DELAY_MILLIS = 2_000L
    }
}

/**
 * root-level restore result通知のUI状態。
 *
 * [waitingForCompletion]がtrueの場合、resultは存在するがmarkerがまだ通知可能な終端状態ではない。
 */
data class PendingRestoreResultUiState(
    val notification: PendingRestoreNotificationUiModel? = null,
    val waitingForCompletion: Boolean = false,
)

/**
 * Snackbar表示に必要な最小限のrestore通知モデル。
 *
 * 内部diagnostic messageやfilesystem pathはUI層へ渡さない。
 */
data class PendingRestoreNotificationUiModel(
    val token: String,
    val type: PendingRestoreNotificationType,
)

/** Pending packageの通知モデルをUI表示モデルへ変換する。 */
private fun PendingRestoreNotification.toUiModel(): PendingRestoreNotificationUiModel =
    PendingRestoreNotificationUiModel(token = token, type = type)
