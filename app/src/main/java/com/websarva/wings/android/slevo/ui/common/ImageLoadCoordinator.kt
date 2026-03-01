package com.websarva.wings.android.slevo.ui.common

import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 画像読み込み状態をURL単位で一元管理する調停役。
 *
 * 画面を跨いだ状態同期と、旧リクエストの完了通知の無効化を担う。
 */
class ImageLoadCoordinator {
    private val lock = Any()
    private val requestIdByUrl = mutableMapOf<String, Long>()
    private val _stateByUrl = MutableStateFlow<Map<String, ImageLoadEntry>>(emptyMap())
    val stateByUrl: StateFlow<Map<String, ImageLoadEntry>> = _stateByUrl.asStateFlow()

    /**
     * URL単位の読み込み状態と世代情報を保持する。
     */
    data class ImageLoadEntry(
        val status: ImageLoadStatus,
        val failureType: ImageLoadFailureType? = null,
        val requestId: Long = 0L,
    )

    /**
     * 画像読み込みの状態種別。
     */
    enum class ImageLoadStatus {
        IDLE,
        LOADING,
        SUCCESS,
        FAILURE,
    }

    /**
     * 読み込み開始の世代IDを更新し、状態を読み込み中にする。
     */
    fun onLoadStart(imageUrl: String): Long {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは管理対象にしない。
            return 0L
        }
        val currentEntry = _stateByUrl.value[imageUrl]
        if (currentEntry?.status == ImageLoadStatus.LOADING ||
            currentEntry?.status == ImageLoadStatus.SUCCESS
        ) {
            // Guard: 既に進行中または成功済みのURLは世代を進めない。
            return currentEntry.requestId
        }
         if (currentEntry?.status == ImageLoadStatus.FAILURE) {
             // Guard: 失敗状態は明示リトライまで再取得しない。
             return 0L
         }
        val requestId = synchronized(lock) {
            val nextId = (requestIdByUrl[imageUrl] ?: 0L) + 1L
            requestIdByUrl[imageUrl] = nextId
            nextId
        }
        _stateByUrl.update { current ->
            val updated = current.toMutableMap()
            updated[imageUrl] = ImageLoadEntry(
                status = ImageLoadStatus.LOADING,
                requestId = requestId,
            )
            updated
        }
        return requestId
    }

    /**
     * 読み込み成功通知を適用する。
     */
    fun onLoadSuccess(imageUrl: String, requestId: Long) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは管理対象にしない。
            return
        }
        if (!isCurrentRequest(imageUrl, requestId)) {
            // Guard: 旧世代の完了通知は無効化する。
            return
        }
        _stateByUrl.update { current ->
            val updated = current.toMutableMap()
            updated[imageUrl] = ImageLoadEntry(
                status = ImageLoadStatus.SUCCESS,
                requestId = requestId,
            )
            updated
        }
        // Guard: 成功時は状態だけを更新する。
    }

    /**
     * 読み込み失敗通知を適用する。
     */
    fun onLoadError(imageUrl: String, requestId: Long, failureType: ImageLoadFailureType) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは管理対象にしない。
            return
        }
        if (!isCurrentRequest(imageUrl, requestId)) {
            // Guard: 旧世代の完了通知は無効化する。
            return
        }
        val currentStatus = _stateByUrl.value[imageUrl]?.status
        if (currentStatus != ImageLoadStatus.LOADING) {
            // Guard: 読み込み中以外の状態は失敗通知で上書きしない。
            return
        }
        _stateByUrl.update { current ->
            val updated = current.toMutableMap()
            updated[imageUrl] = ImageLoadEntry(
                status = ImageLoadStatus.FAILURE,
                failureType = failureType,
                requestId = requestId,
            )
            updated
        }
        // Guard: 失敗時は状態だけを更新する。
    }

    /**
     * リクエストキャンセル通知を読み込み中解除として扱う。
     */
    fun onLoadCancel(imageUrl: String, requestId: Long) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは管理対象にしない。
            return
        }
        if (!isCurrentRequest(imageUrl, requestId)) {
            // Guard: 旧世代のキャンセル通知は無効化する。
            return
        }
        val currentStatus = _stateByUrl.value[imageUrl]?.status
        if (currentStatus != ImageLoadStatus.LOADING) {
            // Guard: 読み込み中以外のキャンセル通知は反映しない。
            return
        }
        _stateByUrl.update { current ->
            val updated = current.toMutableMap()
            updated[imageUrl] = ImageLoadEntry(
                status = ImageLoadStatus.IDLE,
                requestId = requestId,
            )
            updated
        }
        // Guard: キャンセル通知は状態だけを更新する。
    }

    /**
     * 明示リトライ開始時に状態をリセットする。
     */
    fun resetForRetry(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLはリトライ対象にしない。
            return
        }
        _stateByUrl.update { current ->
            val updated = current.toMutableMap()
            updated[imageUrl] = ImageLoadEntry(
                status = ImageLoadStatus.IDLE,
                requestId = current[imageUrl]?.requestId ?: 0L,
            )
            updated
        }
    }

    /**
     * 指定URLの進捗状態と読み込み状態を初期化する。
     */
    fun clearUrl(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは対象にしない。
            return
        }
        // Guard: URL単位の状態破棄のみ行う。
        synchronized(lock) {
            requestIdByUrl.remove(imageUrl)
        }
        _stateByUrl.update { current -> current - imageUrl }
    }

    /**
     * 状態未設定のURLに読み込み成功状態を補完する。
     */
    fun ensureSuccessIfMissing(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは対象にしない。
            return
        }
        _stateByUrl.update { current ->
            if (current.containsKey(imageUrl)) {
                return@update current
            }
            current + (imageUrl to ImageLoadEntry(status = ImageLoadStatus.SUCCESS))
        }
    }

    /**
     * 現行世代のリクエストであるか確認する。
     */
    private fun isCurrentRequest(imageUrl: String, requestId: Long): Boolean {
        val currentId = synchronized(lock) { requestIdByUrl[imageUrl] ?: 0L }
        return requestId != 0L && currentId == requestId
    }

    /**
     * 最新の世代IDを取得する。
     */
    fun latestRequestId(imageUrl: String): Long {
        return synchronized(lock) { requestIdByUrl[imageUrl] ?: 0L }
    }

    /**
     * URLごとのローディング状態を判定する。
     */
    fun isLoading(imageUrl: String): Boolean {
        return _stateByUrl.value[imageUrl]?.status == ImageLoadStatus.LOADING
    }

    /**
     * URLの失敗種別を取得する。
     */
    fun failureType(imageUrl: String): ImageLoadFailureType? {
        return _stateByUrl.value[imageUrl]?.failureType
    }

    /**
     * URLの読み込み状態を取得する。
     */
    fun status(imageUrl: String): ImageLoadStatus? {
        return _stateByUrl.value[imageUrl]?.status
    }

}
