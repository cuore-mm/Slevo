package com.websarva.wings.android.slevo.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveCoordinator
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSavePreparation
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 画像ビューア画面の UI 状態を管理する ViewModel。
 *
 * 画面内メニューの開閉状態など、描画に必要な状態を保持する。
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()
    private val imageSaveCoordinator = ImageSaveCoordinator()
    private val _imageSaveEvents = MutableSharedFlow<ImageSaveUiEvent>(extraBufferCapacity = 1)
    val imageSaveEvents: SharedFlow<ImageSaveUiEvent> = _imageSaveEvents.asSharedFlow()

    fun showTopBarMenu() {
        _uiState.update { it.copy(isTopBarMenuExpanded = true) }
    }

    fun toggleTopBarMenu() {
        _uiState.update { state ->
            state.copy(isTopBarMenuExpanded = !state.isTopBarMenuExpanded)
        }
    }

    fun hideTopBarMenu() {
        _uiState.update { it.copy(isTopBarMenuExpanded = false) }
    }

    fun toggleBarsVisibility() {
        _uiState.update { state ->
            state.copy(isBarsVisible = !state.isBarsVisible)
        }
    }

    fun setBarsVisibility(isVisible: Boolean) {
        _uiState.update { state ->
            state.copy(isBarsVisible = isVisible)
        }
    }

    /**
     * 表示対象URLに合わせて失敗状態URLを整合させる。
     */
    fun synchronizeFailedImageUrls(imageUrls: List<String>) {
        val activeUrls = imageUrls.asSequence().filter { it.isNotBlank() }.toSet()
        _uiState.update { state ->
            state.copy(
                viewerImageLoadFailureByUrl = state.viewerImageLoadFailureByUrl.filterKeys { url ->
                    url in activeUrls
                },
                viewerImageLoadingUrls = state.viewerImageLoadingUrls
                    .filter { url -> url in activeUrls }
                    .toSet(),
                thumbnailImageLoadFailureByUrl =
                    state.thumbnailImageLoadFailureByUrl.filterKeys { url -> url in activeUrls },
                thumbnailRetryNonceByUrl =
                    state.thumbnailRetryNonceByUrl.filterKeys { url -> url in activeUrls },
            )
        }
    }

    /**
     * 本体画像読み込み開始URLを読み込み中状態へ追加する。
     */
    fun onViewerImageLoadStart(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは読み込み管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(viewerImageLoadingUrls = state.viewerImageLoadingUrls + imageUrl)
        }
    }

    /**
     * 画像読み込み失敗URLを失敗状態へ追加する。
     */
    fun onViewerImageLoadError(imageUrl: String, failureType: ImageLoadFailureType) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(
                viewerImageLoadFailureByUrl = state.viewerImageLoadFailureByUrl +
                    (imageUrl to failureType),
                viewerImageLoadingUrls = state.viewerImageLoadingUrls - imageUrl,
            )
        }
    }

    /**
     * 画像読み込み成功URLを失敗状態から解除する。
     */
    fun onViewerImageLoadSuccess(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(
                viewerImageLoadFailureByUrl = state.viewerImageLoadFailureByUrl - imageUrl,
                viewerImageLoadingUrls = state.viewerImageLoadingUrls - imageUrl,
            )
        }
    }

    /**
     * 明示リトライ開始時に対象URLの失敗状態を解除する。
     */
    fun onViewerImageRetry(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            val currentThumbnailNonce = state.thumbnailRetryNonceByUrl[imageUrl] ?: 0
            state.copy(
                viewerImageLoadFailureByUrl = state.viewerImageLoadFailureByUrl - imageUrl,
                viewerImageLoadingUrls = state.viewerImageLoadingUrls - imageUrl,
                thumbnailImageLoadFailureByUrl = state.thumbnailImageLoadFailureByUrl - imageUrl,
                thumbnailRetryNonceByUrl =
                    state.thumbnailRetryNonceByUrl + (imageUrl to (currentThumbnailNonce + 1)),
            )
        }
    }

    /**
     * 下部サムネイル読み込み失敗URLを失敗状態へ追加する。
     */
    fun onThumbnailImageLoadError(imageUrl: String, failureType: ImageLoadFailureType) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(
                thumbnailImageLoadFailureByUrl = state.thumbnailImageLoadFailureByUrl +
                    (imageUrl to failureType)
            )
        }
    }

    /**
     * 下部サムネイル読み込み成功URLを失敗状態から解除する。
     */
    fun onThumbnailImageLoadSuccess(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(
                thumbnailImageLoadFailureByUrl = state.thumbnailImageLoadFailureByUrl - imageUrl
            )
        }
    }

    /**
     * 画像保存要求を受け取り、権限判定に応じて処理を進める。
     */
    fun requestImageSave(context: android.content.Context, urls: List<String>) {
        when (val preparation = imageSaveCoordinator.prepareSave(context, urls)) {
            ImageSavePreparation.Ignore -> Unit
            is ImageSavePreparation.RequestPermission -> {
                _imageSaveEvents.tryEmit(ImageSaveUiEvent.RequestPermission(preparation.permission))
            }

            is ImageSavePreparation.ReadyToSave -> {
                launchImageSave(context, preparation.urls)
            }
        }
    }

    /**
     * 権限要求の結果を受け取り、許可時は保留していた保存処理を再開する。
     */
    fun onImageSavePermissionResult(context: android.content.Context, granted: Boolean) {
        if (!granted) {
            imageSaveCoordinator.clearPendingUrls()
            _imageSaveEvents.tryEmit(
                ImageSaveUiEvent.ShowToast(
                    imageSaveCoordinator.buildPermissionDeniedMessage(context)
                )
            )
            return
        }
        val pendingUrls = imageSaveCoordinator.consumePendingUrls()
        if (pendingUrls.isEmpty()) {
            return
        }
        launchImageSave(context, pendingUrls)
    }

    /**
     * 指定URL一覧の保存処理を実行し、進行中通知と結果通知イベントを発行する。
     */
    private fun launchImageSave(context: android.content.Context, urls: List<String>) {
        if (urls.isEmpty()) {
            // Guard: 空URL一覧では保存処理を開始しない。
            return
        }
        _imageSaveEvents.tryEmit(
            ImageSaveUiEvent.ShowToast(imageSaveCoordinator.buildInProgressMessage(context))
        )
        viewModelScope.launch {
            val summary = imageSaveCoordinator.saveImageUrls(context, urls)
            val resultMessage = imageSaveCoordinator.buildResultMessage(
                context = context,
                requestCount = urls.size,
                summary = summary,
            )
            _imageSaveEvents.emit(ImageSaveUiEvent.ShowToast(resultMessage))
        }
    }

    /**
     * 画像URLを対象に NG ダイアログを開く。
     */
    fun openImageNgDialog(url: String) {
        if (url.isBlank()) {
            // Guard: 空URLは NG 登録対象にしない。
            return
        }
        _uiState.update {
            it.copy(
                showImageNgDialog = true,
                imageNgTargetUrl = url,
            )
        }
    }

    /**
     * 画像URLの NG ダイアログを閉じる。
     */
    fun closeImageNgDialog() {
        _uiState.update {
            it.copy(
                showImageNgDialog = false,
                imageNgTargetUrl = null,
            )
        }
    }
}

/**
 * 画像ビューア画面の描画状態。
 *
 * バー表示可否、トップバーのその他メニュー開閉、NGダイアログ表示対象を保持する。
 */
data class ImageViewerUiState(
    val isBarsVisible: Boolean = true,
    val isTopBarMenuExpanded: Boolean = false,
    val showImageNgDialog: Boolean = false,
    val imageNgTargetUrl: String? = null,
    val viewerImageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    val viewerImageLoadingUrls: Set<String> = emptySet(),
    val thumbnailImageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    val thumbnailRetryNonceByUrl: Map<String, Int> = emptyMap(),
)
