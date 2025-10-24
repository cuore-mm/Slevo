package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<S> : ViewModel() where S : BaseUiState<S> {
    protected abstract val _uiState: MutableStateFlow<S>
    val uiState: StateFlow<S> get() = _uiState

    protected var bookmarkViewModel: SingleBookmarkViewModel? = null

    private val identityHistoryObservers = mutableMapOf<String, IdentityHistoryObserver>()

    private var isInitialized = false

    protected fun filterIdentityHistories(source: List<String>, query: String): List<String> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) {
            source
        } else {
            source.filter { it.contains(normalized, ignoreCase = true) }
        }
    }

    /**
     * ViewModelの初期化を行う標準メソッド。
     * UI側からはこのメソッドを呼び出すように統一する。
     * @param force trueの場合、すでに初期化済みでも強制的に再実行する（更新処理など）
     */
    fun initialize(force: Boolean = false) {
        if (!force && isInitialized) return // 強制でない限り、再初期化はしない
        isInitialized = true
        viewModelScope.launch {
            // 具象クラスで実装されるデータ読み込み処理を呼び出す
            loadData(isRefresh = force)
        }
    }

    /**
     * データの読み込み処理。具象クラスで必ず実装する。
     * @param isRefresh trueの場合はキャッシュを無視した強制的な更新を意図する
     */
    protected abstract suspend fun loadData(isRefresh: Boolean)

    fun release() {
        onCleared()
    }

    protected fun bookmarkSaveBookmark(groupId: Long) = bookmarkViewModel?.saveBookmark(groupId)

    protected fun bookmarkUnbookmark() = bookmarkViewModel?.unbookmark()

    protected fun bookmarkOpenAddGroupDialog() = bookmarkViewModel?.openAddGroupDialog()

    protected fun bookmarkOpenEditGroupDialog(group: Groupable) = bookmarkViewModel?.openEditGroupDialog(group)

    protected fun bookmarkCloseAddGroupDialog() = bookmarkViewModel?.closeAddGroupDialog()

    protected fun bookmarkSetEnteredGroupName(name: String) = bookmarkViewModel?.setEnteredGroupName(name)

    protected fun bookmarkSetSelectedColor(color: String) = bookmarkViewModel?.setSelectedColor(color)

    protected fun bookmarkConfirmGroup() = bookmarkViewModel?.confirmGroup()

    protected fun bookmarkRequestDeleteGroup() = bookmarkViewModel?.requestDeleteGroup()

    protected fun bookmarkConfirmDeleteGroup() = bookmarkViewModel?.confirmDeleteGroup()

    protected fun bookmarkCloseDeleteGroupDialog() = bookmarkViewModel?.closeDeleteGroupDialog()

    protected fun bookmarkOpenBookmarkSheet() = bookmarkViewModel?.openBookmarkSheet()

    protected fun bookmarkCloseBookmarkSheet() = bookmarkViewModel?.closeBookmarkSheet()

    protected fun prepareIdentityHistory(
        key: String,
        boardId: Long,
        repository: PostHistoryRepository,
        onLastIdentity: ((String, String) -> Unit)? = null,
        onNameSuggestions: (List<String>) -> Unit,
        onMailSuggestions: (List<String>) -> Unit,
        nameQueryProvider: () -> String,
        mailQueryProvider: () -> String,
    ) {
        val observer = identityHistoryObservers.getOrPut(key) {
            IdentityHistoryObserver(
                onLastIdentity = onLastIdentity,
                onNameSuggestions = onNameSuggestions,
                onMailSuggestions = onMailSuggestions,
                nameQueryProvider = nameQueryProvider,
                mailQueryProvider = mailQueryProvider,
            )
        }.apply {
            this.onLastIdentity = onLastIdentity
            this.onNameSuggestions = onNameSuggestions
            this.onMailSuggestions = onMailSuggestions
            this.nameQueryProvider = nameQueryProvider
            this.mailQueryProvider = mailQueryProvider
        }

        if (observer.boardId == boardId) {
            refreshIdentityHistorySuggestions(key)
            return
        }

        observer.boardId = boardId
        observer.nameJob?.cancel()
        observer.mailJob?.cancel()
        observer.latestNames = emptyList()
        observer.latestMails = emptyList()
        refreshIdentityHistorySuggestions(key)

        if (boardId == 0L) {
            return
        }

        viewModelScope.launch {
            repository.getLastIdentity(boardId)?.let { identity ->
                observer.onLastIdentity?.invoke(identity.name, identity.email)
            }
        }

        observer.nameJob = viewModelScope.launch {
            repository.observeIdentityHistories(boardId, PostIdentityType.NAME).collect { histories ->
                observer.latestNames = histories
                refreshIdentityHistorySuggestions(key, PostIdentityType.NAME)
            }
        }
        observer.mailJob = viewModelScope.launch {
            repository.observeIdentityHistories(boardId, PostIdentityType.EMAIL).collect { histories ->
                observer.latestMails = histories
                refreshIdentityHistorySuggestions(key, PostIdentityType.EMAIL)
            }
        }
    }

    protected fun refreshIdentityHistorySuggestions(
        key: String,
        type: PostIdentityType? = null,
    ) {
        val observer = identityHistoryObservers[key] ?: return
        if (type == null || type == PostIdentityType.NAME) {
            val suggestions = filterIdentityHistories(observer.latestNames, observer.nameQueryProvider())
            observer.onNameSuggestions.invoke(suggestions)
        }
        if (type == null || type == PostIdentityType.EMAIL) {
            val suggestions = filterIdentityHistories(observer.latestMails, observer.mailQueryProvider())
            observer.onMailSuggestions.invoke(suggestions)
        }
    }

    protected fun deleteIdentityHistory(
        key: String,
        repository: PostHistoryRepository,
        type: PostIdentityType,
        value: String,
    ) {
        val observer = identityHistoryObservers[key] ?: return
        val normalized = value.trim()
        if (normalized.isEmpty()) return
        when (type) {
            PostIdentityType.NAME -> {
                observer.latestNames = observer.latestNames.filterNot { it == normalized }
            }

            PostIdentityType.EMAIL -> {
                observer.latestMails = observer.latestMails.filterNot { it == normalized }
            }
        }
        refreshIdentityHistorySuggestions(key, type)
        val boardId = observer.boardId
        if (boardId == 0L) return
        viewModelScope.launch {
            repository.deleteIdentity(boardId, type, normalized)
        }
    }

    private class IdentityHistoryObserver(
        var onLastIdentity: ((String, String) -> Unit)?,
        var onNameSuggestions: (List<String>) -> Unit,
        var onMailSuggestions: (List<String>) -> Unit,
        var nameQueryProvider: () -> String,
        var mailQueryProvider: () -> String,
    ) {
        var boardId: Long = -1L
        var nameJob: Job? = null
        var mailJob: Job? = null
        var latestNames: List<String> = emptyList()
        var latestMails: List<String> = emptyList()
    }
}
