package com.websarva.wings.android.slevo.ui.util

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 画像読み込み進捗状態を保持するレジストリ。
 *
 * 内部では request 単位で進捗を追跡し、UI には URL 単位へ集約した
 * 進捗状態を `StateFlow` として公開する。
 */
object ImageLoadProgressRegistry {
    /**
     * request 単位で保持する進捗エントリ。
     */
    private data class RequestProgressEntry(
        val url: String,
        val state: ImageLoadProgressState,
    )

    private val lock = Any()
    private val requestIdCounter = AtomicLong(0L)
    private val progressByRequestId = mutableMapOf<String, RequestProgressEntry>()
    private val _progressByUrl = MutableStateFlow<Map<String, ImageLoadProgressState>>(emptyMap())
    val progressByUrl: StateFlow<Map<String, ImageLoadProgressState>> = _progressByUrl.asStateFlow()

    /**
     * 進捗追跡用の request ID を生成する。
     */
    fun createRequestId(): String {
        val sequence = requestIdCounter.incrementAndGet()
        return "image-request-$sequence"
    }

    /**
     * 指定 request の読み込み開始を登録する。
     *
     * 進捗率が未確定のため、初期状態は無段階表示とする。
     */
    fun start(requestId: String, url: String) {
        if (requestId.isBlank() || url.isBlank()) {
            // Guard: 空 requestId / 空 URL は進捗管理対象にしない。
            return
        }
        synchronized(lock) {
            progressByRequestId[requestId] = RequestProgressEntry(
                url = url,
                state = ImageLoadProgressState.Indeterminate,
            )
            _progressByUrl.value = aggregateProgressByUrl(progressByRequestId.values)
        }
    }

    /**
     * 指定 request の受信済みバイト数に応じて進捗を更新する。
     *
     * 総バイト数が不明な場合は無段階表示を維持する。
     */
    fun update(requestId: String, url: String, bytesRead: Long, contentLength: Long) {
        if (requestId.isBlank() || url.isBlank()) {
            // Guard: 空 requestId / 空 URL は進捗更新対象にしない。
            return
        }
        val nextState = if (contentLength > 0L) {
            val clampedProgress = (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
            ImageLoadProgressState.Determinate(progress = clampedProgress)
        } else {
            ImageLoadProgressState.Indeterminate
        }
        synchronized(lock) {
            val currentEntry = progressByRequestId[requestId] ?: return
            progressByRequestId[requestId] = currentEntry.copy(state = nextState)
            _progressByUrl.value = aggregateProgressByUrl(progressByRequestId.values)
        }
    }

    /**
     * 指定 request の読み込み進捗を破棄する。
     *
     * 成功・失敗・キャンセルのいずれでも呼び出してよい。
     */
    fun finish(requestId: String) {
        if (requestId.isBlank()) {
            // Guard: 空 requestId は進捗破棄対象にしない。
            return
        }
        synchronized(lock) {
            progressByRequestId.remove(requestId)
            _progressByUrl.value = aggregateProgressByUrl(progressByRequestId.values)
        }
    }

    /**
     * テスト用に保持状態を初期化する。
     */
    internal fun clearForTest() {
        synchronized(lock) {
            progressByRequestId.clear()
            requestIdCounter.set(0L)
            _progressByUrl.value = emptyMap()
        }
    }

    /**
     * request 単位の進捗状態を URL 単位へ集約する。
     *
     * 同一 URL に無段階表示が 1 件でもあれば無段階を優先し、
     * それ以外は段階表示の最大進捗率を採用する。
     */
    private fun aggregateProgressByUrl(
        requestEntries: Collection<RequestProgressEntry>
    ): Map<String, ImageLoadProgressState> {
        val statesByUrl = mutableMapOf<String, MutableList<ImageLoadProgressState>>()
        requestEntries.forEach { entry ->
            val bucket = statesByUrl.getOrPut(entry.url) { mutableListOf() }
            bucket += entry.state
        }
        return statesByUrl.mapValues { (_, states) ->
            val hasIndeterminate = states.any { it is ImageLoadProgressState.Indeterminate }
            if (hasIndeterminate) {
                ImageLoadProgressState.Indeterminate
            } else {
                val maxProgress = states
                    .asSequence()
                    .filterIsInstance<ImageLoadProgressState.Determinate>()
                    .maxOfOrNull { it.progress }
                    ?: 0f
                ImageLoadProgressState.Determinate(maxProgress)
            }
        }
    }
}

/**
 * 画像読み込み進捗の表示状態。
 *
 * `Determinate` は進捗率表示、`Indeterminate` は進捗率不明時の表示を表す。
 */
sealed interface ImageLoadProgressState {
    /**
     * 進捗率不明の読み込み状態。
     */
    data object Indeterminate : ImageLoadProgressState

    /**
     * 進捗率が算出可能な読み込み状態。
     */
    data class Determinate(
        val progress: Float,
    ) : ImageLoadProgressState
}

/**
 * 画像読み込み進捗インジケータを表示する共通コンポーネント。
 *
 * 進捗率が算出可能な場合は段階表示、算出不能な場合は無段階表示とする。
 *
 * @param progressState 表示する進捗状態
 * @param indicatorSize インジケータのサイズ
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ImageLoadProgressIndicator(
    progressState: ImageLoadProgressState?,
    indicatorSize: Dp,
) {
    when (progressState) {
        is ImageLoadProgressState.Determinate -> {
            CircularWavyProgressIndicator(
                progress = { progressState.progress },
                modifier = Modifier.size(indicatorSize),
            )
        }

        ImageLoadProgressState.Indeterminate,
        null,
            -> {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(indicatorSize),
            )
        }
    }
}
