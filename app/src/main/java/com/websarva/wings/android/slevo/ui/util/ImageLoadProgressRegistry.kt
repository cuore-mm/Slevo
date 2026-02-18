package com.websarva.wings.android.slevo.ui.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 画像URL単位の読み込み進捗状態を保持するレジストリ。
 *
 * Coil/OkHttp 側の進捗通知を受け取り、Compose UI から購読可能な
 * `StateFlow` として公開する。
 */
object ImageLoadProgressRegistry {
    private val _progressByUrl = MutableStateFlow<Map<String, ImageLoadProgressState>>(emptyMap())
    val progressByUrl: StateFlow<Map<String, ImageLoadProgressState>> = _progressByUrl.asStateFlow()

    /**
     * 指定URLの読み込み開始を登録する。
     *
     * 進捗率が未確定のため、初期状態は無段階表示とする。
     */
    fun start(url: String) {
        if (url.isBlank()) {
            // Guard: 空URLは進捗管理対象にしない。
            return
        }
        _progressByUrl.update { current ->
            current + (url to ImageLoadProgressState.Indeterminate)
        }
    }

    /**
     * 受信済みバイト数に応じて進捗を更新する。
     *
     * 総バイト数が不明な場合は無段階表示を維持する。
     */
    fun update(url: String, bytesRead: Long, contentLength: Long) {
        if (url.isBlank()) {
            // Guard: 空URLは進捗更新対象にしない。
            return
        }
        val nextState = if (contentLength > 0L) {
            val clampedProgress = (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
            ImageLoadProgressState.Determinate(progress = clampedProgress)
        } else {
            ImageLoadProgressState.Indeterminate
        }
        _progressByUrl.update { current ->
            current + (url to nextState)
        }
    }

    /**
     * 指定URLの読み込み進捗を破棄する。
     *
     * 成功・失敗・キャンセルのいずれでも呼び出してよい。
     */
    fun finish(url: String) {
        if (url.isBlank()) {
            // Guard: 空URLは進捗破棄対象にしない。
            return
        }
        _progressByUrl.update { current ->
            current - url
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
