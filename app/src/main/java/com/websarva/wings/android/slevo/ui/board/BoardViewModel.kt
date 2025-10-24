package com.websarva.wings.android.slevo.ui.board

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.ImageUploadRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.PostResult
import com.websarva.wings.android.slevo.data.repository.ThreadCreateRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.ui.bbsroute.BaseViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkViewModelFactory
import com.websarva.wings.android.slevo.ui.util.toHiragana
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BoardViewModel @AssistedInject constructor(
    private val repository: BoardRepository,
    private val threadCreateRepository: ThreadCreateRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val postHistoryRepository: PostHistoryRepository,
    private val singleBookmarkViewModelFactory: SingleBookmarkViewModelFactory,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    @Assisted("viewModelKey") val viewModelKey: String
) : BaseViewModel<BoardUiState>() {

    // 元のスレッドリストを保持
    private var originalThreads: List<ThreadInfo>? = null
    private var baseThreads: List<ThreadInfo> = emptyList()
    private var currentHistoryMap: Map<String, Int> = emptyMap()
    private var isObservingThreads: Boolean = false
    private var initializedUrl: String? = null

    override val _uiState = MutableStateFlow(BoardUiState())
    private var threadTitleNg: List<Pair<Long?, Regex>> = emptyList()

    init {
        viewModelScope.launch {
            settingsRepository.observeGestureSettings().collect { settings ->
                _uiState.update { it.copy(gestureSettings = settings) }
            }
        }
    }

    fun initializeBoard(boardInfo: BoardInfo) {
        if (initializedUrl == boardInfo.url) return
        initializedUrl = boardInfo.url
        val bookmarkVm = singleBookmarkViewModelFactory.create(boardInfo, null)
        bookmarkViewModel = bookmarkVm

        val serviceName = parseServiceName(boardInfo.url)
        _uiState.update { it.copy(boardInfo = boardInfo, serviceName = serviceName) }

        viewModelScope.launch {
            val ensuredId = repository.ensureBoard(boardInfo)
            val ensuredInfo = boardInfo.copy(boardId = ensuredId)
            _uiState.update { it.copy(boardInfo = ensuredInfo) }

            repository.fetchBoardNoname("${boardInfo.url}SETTING.TXT")?.let { noname ->
                _uiState.update { state ->
                    state.copy(boardInfo = state.boardInfo.copy(noname = noname))
                }
            }

            prepareCreateIdentityHistory(ensuredId)
        }

        viewModelScope.launch {
            bookmarkVm.uiState.collect { bkState ->
                _uiState.update { it.copy(singleBookmarkState = bkState) }
            }
        }

        viewModelScope.launch {
            ngRepository.observeNgs().collect { list ->
                threadTitleNg = list.filter { it.type == NgType.THREAD_TITLE }
                    .mapNotNull { ng ->
                        runCatching {
                            val rx = if (ng.isRegex) {
                                Regex(ng.pattern)
                            } else {
                                Regex(Regex.escape(ng.pattern))
                            }
                            ng.boardId to rx
                        }.getOrNull()
                    }
                applyFiltersAndSort()
            }
        }

        initialize() // BaseViewModelの初期化処理を呼び出す
    }

    override suspend fun loadData(isRefresh: Boolean) {
        var boardInfo = uiState.value.boardInfo
        val boardUrl = boardInfo.url
        if (boardUrl.isBlank()) return
        if (boardInfo.boardId == 0L) {
            val id = repository.ensureBoard(boardInfo)
            boardInfo = boardInfo.copy(boardId = id)
            _uiState.update { it.copy(boardInfo = boardInfo) }
        }

        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        val refreshStartAt = System.currentTimeMillis()
        val normalizedUrl = boardUrl.trimEnd('/')
        try {
            repository.refreshThreadList(
                boardInfo.boardId,
                "$normalizedUrl/subject.txt",
                refreshStartAt,
                isRefresh
            ) { progress ->
                _uiState.update { state -> state.copy(loadProgress = progress) }
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            _uiState.update { it.copy(isLoading = false, loadProgress = 1f, resetScroll = true) }
            mergeHistory(currentHistoryMap)
        }
        if (!isObservingThreads) {
            startObservingThreads(boardInfo.boardId)
        }
    }

    private fun startObservingThreads(boardId: Long) {
        isObservingThreads = true
        viewModelScope.launch {
            combine(
                repository.observeThreads(boardId),
                historyRepository.observeHistoryMap(uiState.value.boardInfo.url)
            ) { threads, historyMap ->
                threads to historyMap
            }.collect { (threads, historyMap) ->
                baseThreads = threads
                currentHistoryMap = historyMap
                if (!_uiState.value.isLoading) {
                    mergeHistory(historyMap)
                }
            }
        }
    }

    fun refreshBoardData() { // Pull-to-refresh 用のメソッド
        initialize(force = true) // 強制的に初期化処理を再実行
    }

    fun consumeResetScroll() {
        _uiState.update { it.copy(resetScroll = false) }
    }

    // --- お気に入り関連の処理はBookmarkStateViewModelに委譲 ---
    fun saveBookmark(groupId: Long) = bookmarkSaveBookmark(groupId)
    fun unbookmarkBoard() = bookmarkUnbookmark()
    fun openAddGroupDialog() = bookmarkOpenAddGroupDialog()
    fun openEditGroupDialog(group: Groupable) = bookmarkOpenEditGroupDialog(group)
    fun closeAddGroupDialog() = bookmarkCloseAddGroupDialog()
    fun setEnteredGroupName(name: String) = bookmarkSetEnteredGroupName(name)
    fun setSelectedColor(color: String) = bookmarkSetSelectedColor(color)
    fun confirmGroup() = bookmarkConfirmGroup()
    fun requestDeleteGroup() = bookmarkRequestDeleteGroup()
    fun confirmDeleteGroup() = bookmarkConfirmDeleteGroup()
    fun closeDeleteGroupDialog() = bookmarkCloseDeleteGroupDialog()
    fun openBookmarkSheet() = bookmarkOpenBookmarkSheet()
    fun closeBookmarkSheet() = bookmarkCloseBookmarkSheet()

    fun setSortKey(sortKey: ThreadSortKey) {
        _uiState.update { it.copy(currentSortKey = sortKey) }
        applyFiltersAndSort()
    }

    fun toggleSortOrder() {
        if (_uiState.value.currentSortKey != ThreadSortKey.DEFAULT) {
            _uiState.update { it.copy(isSortAscending = !it.isSortAscending) }
            applyFiltersAndSort()
        }
    }

    // 検索クエリの更新
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    // 検索モードの切り替え
    fun setSearchMode(isActive: Boolean) {
        _uiState.update { it.copy(isSearchActive = isActive) }
        if (!isActive) {
            // 検索モード終了時にクエリをクリアし、フィルタもリセット
            setSearchQuery("")
        }
    }

    private fun applyFiltersAndSort() {
        originalThreads?.let { allThreads ->
            // 1. フィルタリング
            val query = _uiState.value.searchQuery.toHiragana()
            val searchFiltered = if (query.isNotBlank()) {
                allThreads.filter {
                    it.title.toHiragana().contains(query, ignoreCase = true)
                }
            } else {
                allThreads
            }

            val filteredList = searchFiltered.filterNot { thread ->
                threadTitleNg.any { (bId, rx) ->
                    (bId == null || bId == _uiState.value.boardInfo.boardId) &&
                        rx.containsMatchIn(thread.title)
                }
            }

            // スレッドキーが閾値以上のものを常に末尾に回す
            val (normalThreads, largeKeyThreads) = filteredList.partition { thread ->
                thread.key.toLongOrNull()?.let { it < THREAD_KEY_THRESHOLD } ?: true
            }

            // 2. ソート
            val sortedList = applySort(
                normalThreads,
                _uiState.value.currentSortKey,
                _uiState.value.isSortAscending
            ) + largeKeyThreads

            val (newThreads, existingThreads) = sortedList.partition { it.isNew }
            _uiState.update { it.copy(threads = newThreads + existingThreads) }
        }
    }


    private fun applySort(
        list: List<ThreadInfo>,
        sortKey: ThreadSortKey,
        ascending: Boolean
    ): List<ThreadInfo> {
        if (sortKey == ThreadSortKey.DEFAULT && _uiState.value.searchQuery.isBlank()) {
            // 検索もしていないデフォルトの場合は originalThreads の順序をそのまま使うが、
            // この関数に渡される list は既にフィルタリングされた可能性のあるリスト。
            // ここでは渡された list をソートせずに返すことで「フィルタ後のデフォルト順」とする。
            // 厳密な「サーバーから返ってきた順」は originalThreads を直接使う必要があるが、
            // フィルタリングと組み合わせる場合はこれで良い。
            return list
        }
        // 検索時、またはデフォルト以外のソートキーの場合はソートを行う
        val sortedList = when (sortKey) {
            ThreadSortKey.DEFAULT -> list // フィルタ適用済みの場合、この時点での順序を維持
            ThreadSortKey.MOMENTUM -> list.sortedBy { it.momentum }
            ThreadSortKey.RES_COUNT -> list.sortedBy { it.resCount }
            ThreadSortKey.DATE_CREATED -> list.sortedBy { it.key.toLongOrNull() ?: 0L }
        }
        return if (ascending) sortedList else sortedList.reversed()
    }

    private fun mergeHistory(historyMap: Map<String, Int>) {
        if (baseThreads.isEmpty()) return
        val merged = baseThreads.map { thread ->
            val oldRes = historyMap[thread.key]
            if (oldRes != null) {
                val diff = (thread.resCount - oldRes).coerceAtLeast(0)
                thread.copy(isVisited = true, newResCount = diff, isNew = false)
            } else {
                thread
            }
        }
        originalThreads = merged
        applyFiltersAndSort()
    }

    // Sort BottomSheet 関連
    fun openSortBottomSheet() {
        _uiState.update { it.copy(showSortSheet = true) }
    }

    fun closeSortBottomSheet() {
        _uiState.update { it.copy(showSortSheet = false) }
    }

    fun openInfoDialog() {
        _uiState.update { it.copy(showInfoDialog = true) }
    }

    fun closeInfoDialog() {
        _uiState.update { it.copy(showInfoDialog = false) }
    }

    // --- スレッド作成関連 ---
    fun showCreateDialog() {
        _uiState.update { it.copy(createDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(createDialog = false) }
    }

    fun updateCreateName(name: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(name = name)) }
        refreshCreateIdentityHistory(PostIdentityType.NAME)
    }

    fun updateCreateMail(mail: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(mail = mail)) }
        refreshCreateIdentityHistory(PostIdentityType.EMAIL)
    }

    fun updateCreateTitle(title: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(title = title)) }
    }

    fun updateCreateMessage(message: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(message = message)) }
    }

    fun selectCreateNameHistory(name: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(name = name)) }
        refreshCreateIdentityHistory(PostIdentityType.NAME)
    }

    fun selectCreateMailHistory(mail: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(mail = mail)) }
        refreshCreateIdentityHistory(PostIdentityType.EMAIL)
    }

    fun deleteCreateNameHistory(name: String) {
        deleteCreateIdentity(PostIdentityType.NAME, name)
    }

    fun deleteCreateMailHistory(mail: String) {
        deleteCreateIdentity(PostIdentityType.EMAIL, mail)
    }

    private fun prepareCreateIdentityHistory(boardId: Long) {
        prepareIdentityHistory(
            key = CREATE_IDENTITY_HISTORY_KEY,
            boardId = boardId,
            repository = postHistoryRepository,
            onLastIdentity = { name, mail ->
                _uiState.update { state ->
                    val form = state.createFormState
                    if (form.name.isEmpty() && form.mail.isEmpty()) {
                        state.copy(
                            createFormState = form.copy(
                                name = name,
                                mail = mail,
                            ),
                        )
                    } else {
                        state
                    }
                }
            },
            onNameSuggestions = { suggestions ->
                _uiState.update { it.copy(createNameHistory = suggestions) }
            },
            onMailSuggestions = { suggestions ->
                _uiState.update { it.copy(createMailHistory = suggestions) }
            },
            nameQueryProvider = { _uiState.value.createFormState.name },
            mailQueryProvider = { _uiState.value.createFormState.mail },
        )
    }

    private fun refreshCreateIdentityHistory(type: PostIdentityType) {
        refreshIdentityHistorySuggestions(CREATE_IDENTITY_HISTORY_KEY, type)
    }

    private fun deleteCreateIdentity(type: PostIdentityType, value: String) {
        deleteIdentityHistory(CREATE_IDENTITY_HISTORY_KEY, postHistoryRepository, type, value)
    }

    fun hideConfirmationScreen() {
        _uiState.update { it.copy(isConfirmationScreen = false) }
    }

    fun hideErrorWebView() {
        _uiState.update { it.copy(showErrorWebView = false, errorHtmlContent = "") }
    }

    fun createThreadFirstPhase(
        host: String,
        board: String,
        title: String,
        name: String,
        mail: String,
        message: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, createDialog = false) }
            val result = threadCreateRepository.createThreadFirstPhase(
                host,
                board,
                title,
                name,
                mail,
                message
            )
            _uiState.update { it.copy(isPosting = false) }
            when (result) {
                is PostResult.Success -> {
                    val formState = uiState.value.createFormState
                    val boardId = uiState.value.boardInfo.boardId
                    _uiState.update {
                        it.copy(
                            postResultMessage = "書き込みに成功しました。",
                            createFormState = CreateThreadFormState(
                                name = formState.name,
                                mail = formState.mail
                            )
                        )
                    }
                    if (boardId != 0L) {
                        postHistoryRepository.recordIdentity(
                            boardId = boardId,
                            name = formState.name,
                            email = formState.mail
                        )
                    }
                    refreshBoardData()
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
                        it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                    }
                }
            }
        }
    }

    fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, isConfirmationScreen = false) }
            val result =
                threadCreateRepository.createThreadSecondPhase(host, board, confirmationData)
            _uiState.update { it.copy(isPosting = false) }
            when (result) {
                is PostResult.Success -> {
                    val formState = uiState.value.createFormState
                    val boardId = uiState.value.boardInfo.boardId
                    _uiState.update {
                        it.copy(
                            postResultMessage = "書き込みに成功しました。",
                            createFormState = CreateThreadFormState(
                                name = formState.name,
                                mail = formState.mail
                            )
                        )
                    }
                    if (boardId != 0L) {
                        postHistoryRepository.recordIdentity(
                            boardId = boardId,
                            name = formState.name,
                            email = formState.mail
                        )
                    }
                    refreshBoardData()
                }

                is PostResult.Error -> {
                    _uiState.update {
                        it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                    }
                }

                is PostResult.Confirm -> {
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

    fun uploadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            bytes?.let {
                val url = imageUploadRepository.uploadImage(it)
                if (url != null) {
                    val msg = uiState.value.createFormState.message
                    _uiState.update { current ->
                        current.copy(createFormState = current.createFormState.copy(message = msg + "\n" + url))
                    }
                }
            }
        }
    }

    override fun onCleared() {
        val boardId = _uiState.value.boardInfo.boardId
        if (boardId != 0L) {
            runBlocking { repository.updateBaseline(boardId, System.currentTimeMillis()) }
        }
        super.onCleared()
    }

    private companion object {
        private const val CREATE_IDENTITY_HISTORY_KEY = "board_create_identity"
    }
}


@AssistedFactory
interface BoardViewModelFactory {
    fun create(
        @Assisted("viewModelKey") viewModelKey: String
    ): BoardViewModel
}

