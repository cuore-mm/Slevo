package com.websarva.wings.android.bbsviewer.ui.thread

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.data.repository.DatRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostResult
import com.websarva.wings.android.bbsviewer.ui.common.BaseViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkViewModelFactory
import com.websarva.wings.android.bbsviewer.ui.util.keyToDatUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ThreadViewModel @AssistedInject constructor(
    private val datRepository: DatRepository,
    private val postRepository: PostRepository,
    private val singleBookmarkViewModelFactory: SingleBookmarkViewModelFactory,
    @Assisted val viewModelKey: String,
) : BaseViewModel<ThreadUiState>() {

    override val _uiState = MutableStateFlow(ThreadUiState())
    private var singleBookmarkViewModel: SingleBookmarkViewModel? = null

    //画面遷移した最初に行う初期処理
    fun initializeThread(
        threadKey: String,
        boardInfo: BoardInfo,
        threadTitle: String
    ) {
        val threadInfo = com.websarva.wings.android.bbsviewer.data.model.ThreadInfo(
            key = threadKey,
            title = threadTitle,
            url = boardInfo.url
        )
        _uiState.update { it.copy(boardInfo = boardInfo, threadInfo = threadInfo) }

        // Factoryを使ってBookmarkStateViewModelを生成
        singleBookmarkViewModel = singleBookmarkViewModelFactory.create(boardInfo, threadInfo)

        // 状態をマージ
        viewModelScope.launch {
            singleBookmarkViewModel?.uiState?.collect { favState ->
                _uiState.update { it.copy(singleBookmarkState = favState) }
            }
        }

        initialize() // BaseViewModelの初期化処理を呼び出す
    }

    override suspend fun loadData(isRefresh: Boolean) {
        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        val datUrl = keyToDatUrl(uiState.value.boardInfo.url, uiState.value.threadInfo.key)

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
                        threadInfo = it.threadInfo.copy(title = title ?: it.threadInfo.title)
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
                Log.e("ThreadViewModel", "Failed to load thread data for URL: $datUrl")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
        }
    }

    fun reloadThread() {
        initialize(force = true) // 強制的に初期化処理を再実行
    }


    // --- お気に入り関連の処理はBookmarkStateViewModelに委譲 ---
    fun saveBookmark(groupId: Long) = singleBookmarkViewModel?.saveBookmark(groupId)
    fun unbookmarkBoard() = singleBookmarkViewModel?.unbookmark()
    fun openAddGroupDialog() = singleBookmarkViewModel?.openAddGroupDialog()
    fun closeAddGroupDialog() = singleBookmarkViewModel?.closeAddGroupDialog()
    fun setEnteredGroupName(name: String) = singleBookmarkViewModel?.setEnteredGroupName(name)
    fun setSelectedColor(color: String) = singleBookmarkViewModel?.setSelectedColor(color)
    fun addGroup() = singleBookmarkViewModel?.addGroup()
    fun openBookmarkSheet() = singleBookmarkViewModel?.openBookmarkSheet()
    fun closeBookmarkSheet() = singleBookmarkViewModel?.closeBookmarkSheet()

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
