package com.websarva.wings.android.slevo.ui.thread.viewmodel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.ImageUploadRepository
import com.websarva.wings.android.slevo.data.repository.PostRepository
import com.websarva.wings.android.slevo.data.repository.PostResult
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.BaseViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkViewModelFactory
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private data class PendingPost(
    val resNum: Int?,
    val content: String,
    val name: String,
    val email: String,
)

class ThreadViewModel @AssistedInject constructor(
    private val datRepository: DatRepository,
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val postHistoryRepository: PostHistoryRepository,
    private val singleBookmarkViewModelFactory: SingleBookmarkViewModelFactory,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    @Assisted val viewModelKey: String,
) : BaseViewModel<ThreadUiState>() {

    override val _uiState = MutableStateFlow(ThreadUiState())
    private var singleBookmarkViewModel: SingleBookmarkViewModel? = null
    private var ngList: List<NgEntity> = emptyList()
    private var compiledNg: List<Triple<Long?, Regex, NgType>> = emptyList()
    private var initializedKey: String? = null
    private var pendingPost: PendingPost? = null
    private var observedThreadHistoryId: Long? = null
    private var postHistoryCollectJob: Job? = null

    //画面遷移した最初に行う初期処理
    fun initializeThread(
        threadKey: String,
        boardInfo: BoardInfo,
        threadTitle: String
    ) {
        val initKey = "$threadKey|${boardInfo.url}"
        if (initializedKey == initKey) return
        initializedKey = initKey
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
            ngRepository.observeNgs().collect { list ->
                ngList = list
                compiledNg = list.mapNotNull { ng ->
                    runCatching {
                        val rx = if (ng.isRegex) {
                            Regex(ng.pattern)
                        } else {
                            // 通常文字列は正規表現メタ文字をエスケープした上で「部分一致」判定に統一
                            Regex(Regex.escape(ng.pattern))
                        }
                        Triple(ng.boardId, rx, ng.type)
                    }.getOrNull()
                }
                updateNgPostNumbers()
            }
        }

        viewModelScope.launch {
            val isTree = settingsRepository.observeIsTreeSort().first()
            _uiState.update { state ->
                state.copy(sortType = if (isTree) ThreadSortType.TREE else ThreadSortType.NUMBER)
            }
            initialize() // BaseViewModelの初期化処理を呼び出す
        }
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
                val derived = deriveReplyMaps(posts)
                val tree = deriveTreeOrder(posts)
                _uiState.update {
                    it.copy(
                        posts = posts,
                        isLoading = false,
                        loadProgress = 1f,
                        threadInfo = it.threadInfo.copy(title = title ?: it.threadInfo.title),
                        idCountMap = derived.first,
                        idIndexList = derived.second,
                        replySourceMap = derived.third,
                        treeOrder = tree.first,
                        treeDepthMap = tree.second,
                    )
                }
                updateNgPostNumbers()
                val historyId = historyRepository.recordHistory(
                    uiState.value.boardInfo,
                    uiState.value.threadInfo.copy(title = title ?: uiState.value.threadInfo.title),
                    posts.size
                )
                if (observedThreadHistoryId != historyId) {
                    observedThreadHistoryId = historyId
                    postHistoryCollectJob?.cancel()
                    postHistoryCollectJob = viewModelScope.launch {
                        postHistoryRepository.observeMyPostNumbers(historyId).collect { nums ->
                            _uiState.update { it.copy(myPostNumbers = nums) }
                        }
                    }
                }
                pendingPost?.let { pending ->
                    val resNumber = pending.resNum ?: posts.size
                    if (resNumber in 1..posts.size) {
                        val p = posts[resNumber - 1]
                        postHistoryRepository.recordPost(
                            content = pending.content,
                            date = parseDateToUnix(p.date),
                            threadHistoryId = historyId,
                            boardId = uiState.value.boardInfo.boardId,
                            resNum = resNumber,
                            name = pending.name,
                            email = pending.email,
                            postId = p.id
                        )
                    }
                    pendingPost = null
                }
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

    private fun deriveTreeOrder(posts: List<ReplyInfo>): Pair<List<Int>, Map<Int, Int>> {
        val children = mutableMapOf<Int, MutableList<Int>>()
        val parent = IntArray(posts.size + 1)
        val depthMap = mutableMapOf<Int, Int>()
        val regex = Regex("^>>(\\d+)")
        posts.forEachIndexed { idx, reply ->
            val current = idx + 1
            val match = regex.find(reply.content)
            val p = match?.groupValues?.get(1)?.toIntOrNull()
            if (p != null && p in 1 until current) {
                parent[current] = p
                children.getOrPut(p) { mutableListOf() }.add(current)
            }
        }
        val order = mutableListOf<Int>()
        fun dfs(num: Int, depth: Int) {
            order.add(num)
            depthMap[num] = depth
            children[num]?.forEach { child -> dfs(child, depth + 1) }
        }
        for (i in 1..posts.size) {
            if (parent[i] == 0) {
                dfs(i, 0)
            }
        }
        return order to depthMap
    }

    private fun parseDateToUnix(dateString: String): Long {
        val sanitized = dateString
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\.\\d+"), "")
            .trim()
        return try {
            DATE_FORMAT.parse(sanitized)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun updateNgPostNumbers() {
        val posts = uiState.value.posts ?: return
        val boardId = uiState.value.boardInfo.boardId
        val ngNumbers = posts.mapIndexedNotNull { idx, post ->
            val isNg = compiledNg.any { (bId, rx, type) ->
                (bId == null || bId == boardId) && runCatching {
                    val target = when (type) {
                        NgType.USER_ID -> post.id
                        NgType.USER_NAME -> post.name
                        NgType.WORD -> post.content
                        else -> ""
                    }
                    rx.containsMatchIn(target)
                }.getOrDefault(false)
            }
            if (isNg) idx + 1 else null
        }.toSet()
        _uiState.update { it.copy(ngPostNumbers = ngNumbers) }
    }
    fun reloadThread() {
        initialize(force = true) // 強制的に初期化処理を再実行
    }

    fun toggleSortType() {
        _uiState.update { state ->
            val next = if (state.sortType == ThreadSortType.NUMBER) {
                ThreadSortType.TREE
            } else {
                ThreadSortType.NUMBER
            }
            state.copy(sortType = next)
        }
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

    fun startSearch() {
        _uiState.update { it.copy(isSearchMode = true) }
    }

    fun closeSearch() {
        _uiState.update { it.copy(isSearchMode = false, searchQuery = "") }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
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
                    _uiState.update {
                        it.copy(
                            postResultMessage = "書き込みに成功しました。"
                        )
                    }
                    pendingPost = PendingPost(result.resNum, message, name, mail)
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
                    _uiState.update {
                        it.copy(
                            postResultMessage = "書き込みに成功しました。"
                        )
                    }
                    val form = uiState.value.postFormState
                    pendingPost = PendingPost(result.resNum, form.message, form.name, form.mail)
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

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        }
    }
}

@AssistedFactory
interface ThreadViewModelFactory {
    fun create(viewModelKey: String): ThreadViewModel
}
