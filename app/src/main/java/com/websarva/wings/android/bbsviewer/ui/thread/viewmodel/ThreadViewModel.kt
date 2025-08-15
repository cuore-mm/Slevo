package com.websarva.wings.android.bbsviewer.ui.thread.viewmodel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.Groupable
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.data.repository.DatRepository
import com.websarva.wings.android.bbsviewer.data.repository.ImageUploadRepository
import com.websarva.wings.android.bbsviewer.data.repository.NgIdRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostRepository
import com.websarva.wings.android.bbsviewer.data.repository.PostResult
import com.websarva.wings.android.bbsviewer.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.bbsviewer.ui.common.BaseViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkViewModelFactory
import com.websarva.wings.android.bbsviewer.ui.thread.state.ReplyInfo
import com.websarva.wings.android.bbsviewer.ui.thread.state.ThreadUiState
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.NgIdEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreadViewModel @AssistedInject constructor(
    private val datRepository: DatRepository,
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val ngIdRepository: NgIdRepository,
    private val singleBookmarkViewModelFactory: SingleBookmarkViewModelFactory,
    @Assisted val viewModelKey: String,
) : BaseViewModel<ThreadUiState>() {

    override val _uiState = MutableStateFlow(ThreadUiState())
    private var singleBookmarkViewModel: SingleBookmarkViewModel? = null
    private var allPosts: List<ReplyInfo> = emptyList()
    private var ngIds: List<NgIdEntity> = emptyList()

    //画面遷移した最初に行う初期処理
    fun initializeThread(
        threadKey: String,
        boardInfo: BoardInfo,
        threadTitle: String
    ) {
        val threadInfo = ThreadInfo(
            key = threadKey,
            title = threadTitle,
            url = boardInfo.url
        )
        _uiState.update { it.copy(boardInfo = boardInfo, threadInfo = threadInfo) }

        viewModelScope.launch {
            boardRepository.fetchBoardNoname("${boardInfo.url}SETTING.TXT")?.let { noname ->
                _uiState.update { state ->
                    state.copy(boardInfo = state.boardInfo.copy(noname = noname))
                }
            }
        }

        // Factoryを使ってBookmarkStateViewModelを生成
        singleBookmarkViewModel = singleBookmarkViewModelFactory.create(boardInfo, threadInfo)

        // 状態をマージ
        viewModelScope.launch {
            singleBookmarkViewModel?.uiState?.collect { favState ->
                _uiState.update { it.copy(singleBookmarkState = favState) }
            }
        }

        viewModelScope.launch {
            ngIdRepository.observeAll().collect { ids ->
                ngIds = ids
                applyNgIdFilter()
            }
        }

        initialize() // BaseViewModelの初期化処理を呼び出す
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadData(isRefresh: Boolean) {
        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        val boardUrl = uiState.value.boardInfo.url
        val key = uiState.value.threadInfo.key

        try {
            val threadData = datRepository.getThread(boardUrl, key) { progress ->
                _uiState.update { it.copy(loadProgress = progress) }
            }
            if (threadData != null) {
                val (posts, title) = threadData
                allPosts = posts
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadProgress = 1f,
                        threadInfo = it.threadInfo.copy(title = title ?: it.threadInfo.title)
                    )
                }
                applyNgIdFilter()
                historyRepository.recordHistory(
                    uiState.value.boardInfo,
                    uiState.value.threadInfo.copy(title = title ?: uiState.value.threadInfo.title),
                    posts.size
                )
            } else {
                _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
                Log.e(
                    "ThreadViewModel",
                    "Failed to load thread data for board: $boardUrl key: $key"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
        }
    }

    private fun deriveReplyMaps(posts: List<ReplyInfo>): Triple<Map<String, Int>, List<Int>, Map<Int, List<Int>>> {
        val idCountMap = posts.groupingBy { it.id }.eachCount()
        val idIndexList = run {
            val indexMap = mutableMapOf<String, Int>()
            posts.map { reply ->
                val idx = (indexMap[reply.id] ?: 0) + 1
                indexMap[reply.id] = idx
                idx
            }
        }
        val replySourceMap = run {
            val map = mutableMapOf<Int, MutableList<Int>>()
            val regex = Regex(">>(\\d+)")
            posts.forEachIndexed { idx, reply ->
                regex.findAll(reply.content).forEach { match ->
                    val num = match.groupValues[1].toIntOrNull() ?: return@forEach
                    if (num in 1..posts.size) {
                        map.getOrPut(num) { mutableListOf() }.add(idx + 1)
                    }
                }
            }
            map.mapValues { it.value.toList() }
        }
        return Triple(idCountMap, idIndexList, replySourceMap)
    }

    private fun applyNgIdFilter() {
        val boardId = uiState.value.boardInfo.boardId
        val filtered = allPosts.filterNot { reply ->
            ngIds.any { ng ->
                (ng.boardId == null || ng.boardId == boardId) &&
                    (if (ng.isRegex) Regex(ng.pattern).containsMatchIn(reply.id) else reply.id == ng.pattern)
            }
        }
        val derived = deriveReplyMaps(filtered)
        _uiState.update {
            it.copy(
                posts = filtered,
                idCountMap = derived.first,
                idIndexList = derived.second,
                replySourceMap = derived.third
            )
        }
    }

    fun reloadThread() {
        initialize(force = true) // 強制的に初期化処理を再実行
    }


    // --- お気に入り関連の処理はBookmarkStateViewModelに委譲 ---
    fun saveBookmark(groupId: Long) = singleBookmarkViewModel?.saveBookmark(groupId)
    fun unbookmarkBoard() = singleBookmarkViewModel?.unbookmark()
    fun openAddGroupDialog() = singleBookmarkViewModel?.openAddGroupDialog()
    fun openEditGroupDialog(group: Groupable) = singleBookmarkViewModel?.openEditGroupDialog(group)
    fun closeAddGroupDialog() = singleBookmarkViewModel?.closeAddGroupDialog()
    fun setEnteredGroupName(name: String) = singleBookmarkViewModel?.setEnteredGroupName(name)
    fun setSelectedColor(color: String) = singleBookmarkViewModel?.setSelectedColor(color)
    fun confirmGroup() = singleBookmarkViewModel?.confirmGroup()
    fun requestDeleteGroup() = singleBookmarkViewModel?.requestDeleteGroup()
    fun confirmDeleteGroup() = singleBookmarkViewModel?.confirmDeleteGroup()
    fun closeDeleteGroupDialog() = singleBookmarkViewModel?.closeDeleteGroupDialog()
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

    // エラーWebViewを閉じる処理
    fun hideErrorWebView() {
        _uiState.update { it.copy(showErrorWebView = false, errorHtmlContent = "") }
    }

    // 初回投稿処理
    fun postFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, postDialog = false) }
            val result =
                postRepository.postTo5chFirstPhase(host, board, threadKey, name, mail, message)

            _uiState.update { it.copy(isPosting = false) }

            when (result) {
                is PostResult.Success -> {
                    // 成功メッセージ表示など
                    _uiState.update { it.copy(postResultMessage = "書き込みに成功しました。") }
                    reloadThread() // スレッドをリロード
                }

                is PostResult.Confirm -> {
                    _uiState.update {
                        it.copy(
                            postConfirmation = result.confirmationData,
                            isConfirmationScreen = true
                        )
                    }
                }

                is PostResult.Error -> {
                    _uiState.update {
                        it.copy(
                            showErrorWebView = true,
                            errorHtmlContent = result.html
                        )
                    }
                }
            }
        }
    }

    // 2回目投稿
    fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, isConfirmationScreen = false) }
            val result = postRepository.postTo5chSecondPhase(
                host,
                board,
                threadKey,
                confirmationData
            )

            _uiState.update { it.copy(isPosting = false) }

            when (result) {
                is PostResult.Success -> {
                    // 成功メッセージ表示など
                    _uiState.update { it.copy(postResultMessage = "書き込みに成功しました。") }
                    reloadThread()
                }

                is PostResult.Error -> {
                    _uiState.update {
                        it.copy(
                            showErrorWebView = true,
                            errorHtmlContent = result.html
                        )
                    }
                }

                is PostResult.Confirm -> {
                    // 2回目でConfirmが返ることは基本ないが念のため
                    _uiState.update {
                        it.copy(
                            postConfirmation = result.confirmationData,
                            isConfirmationScreen = true
                        )
                    }
                }
            }
        }
    }

    fun uploadImage(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val bytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            bytes?.let {
                val url = imageUploadRepository.uploadImage(it)
                if (url != null) {
                    val msg = uiState.value.postFormState.message
                    _uiState.update { current ->
                        current.copy(postFormState = current.postFormState.copy(message = msg + "\n" + url))
                    }
                }
            }
        }
    }

    fun clearPostResultMessage() {
        _uiState.update { it.copy(postResultMessage = null) }
    }

}

@AssistedFactory
interface ThreadViewModelFactory {
    fun create(viewModelKey: String): ThreadViewModel
}
