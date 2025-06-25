package com.websarva.wings.android.bbsviewer.ui.thread

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.data.repository.DatRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostResult
import com.websarva.wings.android.bbsviewer.ui.common.BaseViewModel
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkStateViewModel
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkStateViewModelFactory
import com.websarva.wings.android.bbsviewer.ui.util.keyToDatUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ThreadViewModel @AssistedInject constructor(
    private val datRepository: DatRepository,
    private val postRepository: PostRepository,
    private val bookmarkStateViewModelFactory: BookmarkStateViewModelFactory,
    @Assisted val viewModelKey: String,
) : BaseViewModel<ThreadUiState>() {

    override val _uiState = MutableStateFlow(ThreadUiState())
    private var bookmarkStateViewModel: BookmarkStateViewModel? = null

    fun loadThread(datUrl: String) {
        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val threadData = datRepository.getThread(datUrl) { progress ->
                    _uiState.update { it.copy(loadProgress = progress) }
                }
                if (threadData != null) {
                    val (posts, title) = threadData
                    _uiState.update {
                        it.copy(
                            posts = posts,
                            isLoading = false,
                            loadProgress = 1f,
                            // タイトルが取得できたら更新
                            threadInfo = it.threadInfo.copy(title = title ?: it.threadInfo.title)
                        )
                    }
                } else {
                    // スレッドデータの取得またはパースに失敗した場合の処理
                    // 例: エラーステートを更新する、ログを出すなど
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadProgress = 1f,
                            // posts は null のままか、空のリストにするなど、仕様に応じて設定
                            // posts = null or emptyList()
                            // errorMessage = "スレッドの読み込みに失敗しました" (UIに表示する場合)
                        )
                    }
                    // 必要であればエラーログを出力
                    Log.e("ThreadViewModel", "Failed to load thread data for URL: $datUrl")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadProgress = 1f,
                        // posts = null or emptyList()
                        // errorMessage = "予期せぬエラーが発生しました"
                    )
                }
            }
        }
    }

    //画面遷移した最初に行う初期処理
    fun initializeThread(
        threadKey: String,
        boardInfo: BoardInfo,
        threadTitle: String
    ) {
        val threadInfo = com.websarva.wings.android.bbsviewer.data.model.ThreadInfo(
            key = threadKey,
            title = threadTitle,
            url = boardInfo.url /*...*/
        )
        _uiState.update { it.copy(boardInfo = boardInfo, threadInfo = threadInfo) }

        // Factoryを使ってBookmarkStateViewModelを生成
        bookmarkStateViewModel = bookmarkStateViewModelFactory.create(boardInfo, threadInfo)

        // 状態をマージ
        viewModelScope.launch {
            bookmarkStateViewModel?.uiState?.collect { favState ->
                _uiState.update { it.copy(bookmarkState = favState) }
            }
        }

        loadThread(keyToDatUrl(boardInfo.url, threadKey))
    }


    // --- お気に入り関連の処理はBookmarkStateViewModelに委譲 ---
    fun saveBookmark(groupId: Long) = bookmarkStateViewModel?.saveBookmark(groupId)
    fun unbookmarkBoard() = bookmarkStateViewModel?.unbookmark()
    fun openAddGroupDialog() = bookmarkStateViewModel?.openAddGroupDialog()
    fun closeAddGroupDialog() = bookmarkStateViewModel?.closeAddGroupDialog()
    fun setEnteredGroupName(name: String) = bookmarkStateViewModel?.setEnteredGroupName(name)
    fun setSelectedColor(color: String) = bookmarkStateViewModel?.setSelectedColor(color)
    fun addGroup() = bookmarkStateViewModel?.addGroup()
    fun openBookmarkSheet() = bookmarkStateViewModel?.openBookmarkSheet()
    fun closeBookmarkSheet() = bookmarkStateViewModel?.closeBookmarkSheet()


    fun reloadThread() {
        val datUrl = _uiState.value.threadInfo.datUrl
        if (datUrl.isNotBlank()) {
            loadThread(datUrl)
        }
    }

    // 書き込み画面を表示
    fun showPostDialog() {
        _uiState.update { it.copy(postDialog = true) }
    }

    // 書き込み画面を閉じる
    fun hidePostDialog() {
        _uiState.update { it.copy(postDialog = false) }
    }

    // 書き込み確認画面を閉じる
    fun hideConfirmationScreen() {
        _uiState.update { it.copy(isConfirmationScreen = false) }
    }

    fun updatePostName(name: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(name = name)) }
    }

    fun updatePostMail(mail: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(mail = mail)) }
    }

    fun updatePostMessage(message: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(message = message)) }
    }

    /**
     * 初回投稿の確認フェーズを呼び出す
     */
    fun loadConfirmation(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result =
                postRepository.postTo5chFirstPhase(host, board, threadKey, name, mail, message)
            val confirmationData = result
            _uiState.update { it.copy(postConfirmation = confirmationData) }
            _uiState.update { it.copy(isConfirmationScreen = true) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * 2回目投稿（書き込み実行）
     * 1回目の確認用リクエストから得た hidden パラメータと Cookie を使用して最終投稿を行う。
     */
    fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = postRepository.postTo5chSecondPhase(
                host,
                board,
                threadKey,
                confirmationData
            )
            if (result == PostResult.Success) {
                _uiState.update { it.copy(isConfirmationScreen = false) }
            } else {
                // エラー処理
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

}

@AssistedFactory
interface ThreadViewModelFactory {
    fun create(viewModelKey: String): ThreadViewModel
}
