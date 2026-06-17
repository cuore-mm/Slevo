package com.websarva.wings.android.slevo.ui.tabs.session.holder

import android.content.Context
import android.net.Uri
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogSuccess
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadCreatePostDialogExecutor
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 板タブ固有の UI セッション holder をまとめるコンテナ。
 *
 * ブックマークシートと投稿ダイアログの状態をタブ単位で保持し、
 * タブ削除時にまとめて解放する。
 */
class BoardTabSessionHolder(
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
    private val postDialogControllerFactory: PostDialogController.Factory,
    private val postDialogImageUploaderFactory: PostDialogImageUploader.Factory,
    private val threadCreatePostDialogExecutor: ThreadCreatePostDialogExecutor,
    private val boardTabsCoordinator: BoardTabsCoordinator,
    private val tabKey: String,
    private val boardUrl: String,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** ブックマークシートの状態と操作。 */
    val bookmarkSheetHolder: BookmarkBottomSheetStateHolder =
        bookmarkSheetStateHolderFactory.create(scope)

    private val _postSuccessEvents = MutableSharedFlow<PostDialogSuccess>(extraBufferCapacity = 1)

    /** 投稿成功イベント。RouteViewModel が収集して板一覧更新等の後処理を行う。 */
    val postSuccessEvents: SharedFlow<PostDialogSuccess> = _postSuccessEvents.asSharedFlow()

    /** 投稿ダイアログの状態と操作。 */
    val postDialogController: PostDialogController =
        postDialogControllerFactory.create(
            scope = scope,
            stateAdapter = BoardPostDialogStateAdapter(
                stateReader = { boardTabsCoordinator.getBoardSessionState(boardUrl).postDialogState },
                stateUpdater = { transform ->
                    boardTabsCoordinator.updateBoardSessionState(boardUrl) { current ->
                        current.copy(postDialogState = transform(current.postDialogState))
                    }
                },
            ),
            identityHistoryKey = CREATE_IDENTITY_HISTORY_KEY,
            executor = threadCreatePostDialogExecutor,
            boardIdProvider = {
                boardTabsCoordinator.openBoardTabs.value
                    .find { it.boardUrl == boardUrl }
                    ?.boardId
                    ?: 0L
            },
            onPostSuccess = { success -> _postSuccessEvents.tryEmit(success) },
        )

    private val postDialogImageUploader =
        postDialogImageUploaderFactory.create(scope, Dispatchers.IO)

    /**
     * 投稿ダイアログに画像をアップロードし、URL を本文に追記する。
     */
    fun uploadPostDialogImage(context: Context, uri: Uri) {
        postDialogImageUploader.uploadImage(context, uri) { url ->
            postDialogController.appendImageUrl(url)
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
        private const val CREATE_IDENTITY_HISTORY_KEY = "board_create_identity"
    }
}

/**
 * [BoardTabSessionHolder] を生成するためのファクトリ。
 *
 * Hilt 注入された依存を保持し、タブ key / 板 URL はメソッド引数で受け取る。
 */
class BoardTabSessionHolderFactory @Inject constructor(
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
    private val postDialogControllerFactory: PostDialogController.Factory,
    private val postDialogImageUploaderFactory: PostDialogImageUploader.Factory,
    private val threadCreatePostDialogExecutor: ThreadCreatePostDialogExecutor,
    private val boardTabsCoordinator: BoardTabsCoordinator,
) {

    /**
     * 指定板 URL に紐づく holder を生成する。
     */
    fun create(tabKey: String, boardUrl: String): BoardTabSessionHolder {
        return BoardTabSessionHolder(
            bookmarkSheetStateHolderFactory = bookmarkSheetStateHolderFactory,
            postDialogControllerFactory = postDialogControllerFactory,
            postDialogImageUploaderFactory = postDialogImageUploaderFactory,
            threadCreatePostDialogExecutor = threadCreatePostDialogExecutor,
            boardTabsCoordinator = boardTabsCoordinator,
            tabKey = tabKey,
            boardUrl = boardUrl,
        )
    }
}
