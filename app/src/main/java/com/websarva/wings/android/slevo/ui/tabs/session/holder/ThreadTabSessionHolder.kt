package com.websarva.wings.android.slevo.ui.tabs.session.holder

import android.content.Context
import android.net.Uri
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveCoordinator
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSavePreparation
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogSuccess
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadReplyPostDialogExecutor
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * スレッドタブ固有の UI セッション holder をまとめるコンテナ。
 *
 * ブックマークシート、投稿ダイアログ、画像保存の状態をタブ単位で保持し、
 * タブ削除時にまとめて解放する。
 */
class ThreadTabSessionHolder @AssistedInject constructor(
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
    private val postDialogControllerFactory: PostDialogController.Factory,
    private val postDialogImageUploaderFactory: PostDialogImageUploader.Factory,
    private val replyPostDialogExecutor: ThreadReplyPostDialogExecutor,
    @Assisted private val tabKey: String,
    @Assisted private val threadId: ThreadId,
    private val threadTabsCoordinator: ThreadTabsCoordinator,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** ブックマークシートの状態と操作。 */
    val bookmarkSheetHolder: BookmarkBottomSheetStateHolder =
        bookmarkSheetStateHolderFactory.create(scope)

    private val _postSuccessEvents = MutableSharedFlow<PostDialogSuccess>(extraBufferCapacity = 1)

    /** 投稿成功イベント。RouteViewModel が収集してリロード等の後処理を行う。 */
    val postSuccessEvents: SharedFlow<PostDialogSuccess> = _postSuccessEvents.asSharedFlow()

    /** 投稿ダイアログの状態と操作。 */
    val postDialogController: PostDialogController =
        postDialogControllerFactory.create(
            scope = scope,
            stateAdapter = ThreadPostDialogStateAdapter(
                stateReader = { threadTabsCoordinator.getThreadSessionState(threadId).postDialogState },
                stateUpdater = { transform ->
                    threadTabsCoordinator.updateThreadSessionState(threadId) { current ->
                        current.copy(postDialogState = transform(current.postDialogState))
                    }
                },
            ),
            identityHistoryKey = POST_IDENTITY_HISTORY_KEY,
            executor = replyPostDialogExecutor,
            boardIdProvider = {
                threadTabsCoordinator.openThreadTabs.value
                    .find { it.id == threadId }
                    ?.boardId
                    ?: 0L
            },
            onPostSuccess = { success -> _postSuccessEvents.tryEmit(success) },
        )

    private val postDialogImageUploader =
        postDialogImageUploaderFactory.create(scope, Dispatchers.IO)

    private val imageSaveCoordinator = ImageSaveCoordinator()

    private val _imageSaveEvents = MutableSharedFlow<ImageSaveUiEvent>(extraBufferCapacity = 1)

    /** 画像保存イベント。permission 要求や結果 toast を UI へ通知する。 */
    val imageSaveEvents: SharedFlow<ImageSaveUiEvent> = _imageSaveEvents.asSharedFlow()

    /**
     * 投稿ダイアログに画像をアップロードし、URL を本文に追記する。
     */
    fun uploadPostDialogImage(context: Context, uri: Uri) {
        postDialogImageUploader.uploadImage(context, uri) { url ->
            postDialogController.appendImageUrl(url)
        }
    }

    /**
     * 画像保存要求の前処理を行い、必要に応じて権限要求または保存処理を進める。
     */
    fun requestImageSave(context: Context, urls: List<String>) {
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
     * 権限要求結果を受け取り、許可時は保留中の保存を再開する。
     */
    fun onImageSavePermissionResult(context: Context, granted: Boolean) {
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
     * 指定 URL 一覧の保存処理を実行し、結果を UI へ通知する。
     */
    private fun launchImageSave(context: Context, urls: List<String>) {
        if (urls.isEmpty()) {
            return
        }
        _imageSaveEvents.tryEmit(
            ImageSaveUiEvent.ShowToast(imageSaveCoordinator.buildInProgressMessage(context))
        )
        scope.launch {
            val summary = imageSaveCoordinator.saveImageUrls(context, urls)
            val message = imageSaveCoordinator.buildResultMessage(
                context = context,
                requestCount = urls.size,
                summary = summary,
            )
            _imageSaveEvents.emit(ImageSaveUiEvent.ShowToast(message))
        }
    }

    /**
     * このタブ用の holder リソースを解放する。
     */
    fun dispose() {
        bookmarkSheetHolder.dispose()
        scope.cancel()
    }

    companion object {
        private const val POST_IDENTITY_HISTORY_KEY = "thread_post_identity"
    }
}

/**
 * [ThreadTabSessionHolder] を生成するためのファクトリ。
 */
@AssistedFactory
interface ThreadTabSessionHolderFactory {

    /**
     * 指定タブ key とスレッド ID に紐づく holder を生成する。
     */
    fun create(tabKey: String, threadId: ThreadId): ThreadTabSessionHolder
}
