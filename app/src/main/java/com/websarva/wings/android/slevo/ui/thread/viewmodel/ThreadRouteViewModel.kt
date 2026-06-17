package com.websarva.wings.android.slevo.ui.thread.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.ThreadTarget
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogSuccess
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.PendingThreadPostState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * スレッド画面 route 単位でタブ表示状態を提供する ViewModel。
 *
 * 1つの route ViewModel から tab key ごとの `ThreadUiState` を遅延生成し、
 * 選択中タブの切り替えでは ViewModel 自体を再生成せずに対象タブだけを差し替える。
 */
@HiltViewModel
class ThreadRouteViewModel @Inject constructor(
    private val tabSessionStore: TabSessionStore,
    private val threadViewModelFactory: ThreadViewModelFactory,
) : ViewModel() {

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }

    private val viewModelCache = mutableMapOf<String, ThreadViewModel>()
    private val uiStateCache = mutableMapOf<String, StateFlow<ThreadUiState>>()
    private val postSuccessCollectJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            tabSessionStore.openThreadTabs.collect { tabs ->
                evictClosedTabs(tabs.map { tab -> tab.id.value }.toSet())
            }
        }
        viewModelScope.launch {
            tabSessionStore.openThreadTabs.collect { tabs ->
                attachPostSuccessCollectors(tabs)
            }
        }
    }

    /** 現在選択中のスレッドタブ key。 */
    val selectedTabKey: StateFlow<String?> = tabSessionStore.selectedThreadTabKey

    /**
     * 選択中タブの `UiState` を返す。
     *
     * タブ未選択時は空の `ThreadUiState` を返す。
     */
    val selectedUiState: StateFlow<ThreadUiState> = selectedTabKey
        .flatMapLatest { tabKey ->
            if (tabKey == null) {
                flowOf(ThreadUiState())
            } else {
                uiStateFor(tabKey)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = ThreadUiState(),
        )

    /**
     * 指定タブ key の `UiState` Flow を返す。
     *
     * 同じ key への要求では既存 Flow を再利用し、購読がなくなった後は
     * `WhileSubscribed` に従って共有を停止する。
     */
    fun uiStateFor(tabKey: String): StateFlow<ThreadUiState> {
        return uiStateCache.getOrPut(tabKey) {
            createUiStateFlow(tabKey)
        }
    }

    /**
     * `uiStateFor` と同じ内容を `Flow` として公開する互換 API。
     */
    fun observeUiState(tabKey: String): Flow<ThreadUiState> = uiStateFor(tabKey)


    /**
     * Task 6 移行中に既存の操作 API を再利用するため、対象タブの旧 ViewModel を返す。
     *
     * UI は `UiState` 購読を route ViewModel 経由へ切り替えつつ、詳細な操作委譲だけを
     * 互換レイヤーとして既存 ViewModel に橋渡しする。
     */
    fun legacyViewModel(tabKey: String): ThreadViewModel = threadViewModelFor(tabKey)

    /**
     * 指定スレッドタブのブックマークシート holder を返す。
     */
    fun bookmarkSheetHolderFor(tabKey: String): BookmarkBottomSheetStateHolder {
        return tabSessionStore.threadBookmarkSheetHolder(tabKey)
    }

    /**
     * 指定スレッドタブのブックマークシートを開く。
     */
    fun openBookmarkSheet(tabKey: String) {
        val state = threadViewModelFor(tabKey).uiState.value
        val boardInfo = state.boardInfo
        val threadInfo = state.threadInfo
        if (boardInfo.url.isBlank() || threadInfo.key.isBlank()) {
            // 必要情報が欠けている場合はシートを開かない。
            return
        }
        val targets = listOf(
            ThreadTarget(
                boardInfo = boardInfo,
                threadInfo = threadInfo,
                currentGroupId = state.bookmarkStatusState.selectedGroup?.id,
            )
        )
        tabSessionStore.threadBookmarkSheetHolder(tabKey).open(targets)
    }

    /**
     * 指定スレッドタブの投稿ダイアログコントローラを返す。
     */
    fun postDialogActionsFor(tabKey: String): PostDialogController {
        return tabSessionStore.threadPostDialogController(tabKey)
    }

    /**
     * 指定スレッドタブの画像保存イベント Flow を返す。
     */
    fun imageSaveEventsFor(tabKey: String): SharedFlow<ImageSaveUiEvent> {
        return tabSessionStore.threadImageSaveEvents(tabKey)
    }

    /**
     * 指定スレッドタブの画像保存要求を処理する。
     */
    fun requestImageSave(tabKey: String, context: Context, urls: List<String>) {
        tabSessionStore.threadRequestImageSave(tabKey, context, urls)
    }

    /**
     * 指定スレッドタブの画像保存権限要求結果を処理する。
     */
    fun onImageSavePermissionResult(tabKey: String, context: Context, granted: Boolean) {
        tabSessionStore.threadOnImageSavePermissionResult(tabKey, context, granted)
    }

    /**
     * 指定スレッドタブの投稿ダイアログに画像をアップロードする。
     */
    fun uploadPostDialogImage(tabKey: String, context: Context, uri: Uri) {
        tabSessionStore.threadUploadPostDialogImage(tabKey, context, uri)
    }

    /**
     * 指定スレッドタブを再読み込みする。
     *
     * ViewModel の再生成ではなく、対象タブの既存 ViewModel へ更新要求を送る。
     */
    fun reloadThread(tabKey: String) {
        threadViewModelFor(tabKey).reloadThread()
    }

    /**
     * 指定スレッドタブに下端プル更新を要求する。
     */
    fun reloadThreadFromBottomPull(tabKey: String) {
        threadViewModelFor(tabKey).reloadThreadFromBottomPull()
    }

    /**
     * 自動スクロール下端到達時の更新を、現在表示中タブにだけ伝播する。
     */
    fun onAutoScrollReachedBottom(tabKey: String) {
        if (selectedTabKey.value != tabKey) {
            // 非表示タブの自動更新は行わない。
            return
        }
        threadViewModelFor(tabKey).onAutoScrollReachedBottom()
    }

    /**
     * 開いているスレッドタブの更新を明示操作として開始する。
     */
    fun refreshOpenThreads() {
        tabSessionStore.refreshOpenThreads()
    }

    /**
     * 開いているスレッドタブの更新をキャンセルする。
     */
    fun cancelRefreshOpenThreads() {
        tabSessionStore.cancelRefreshOpenThreads()
    }

    /**
     * タブ key に対応する `ThreadViewModel` を取得し、必要なら初期化を行う。
     */
    private fun threadViewModelFor(tabKey: String): ThreadViewModel {
        return viewModelCache.getOrPut(tabKey) {
            threadViewModelFactory.create(tabKey).also { viewModel ->
                initializeThreadViewModel(viewModel, findTab(tabKey))
            }
        }
    }

    /**
     * タブ key ごとの共有 `UiState` Flow を組み立てる。
     */
    private fun createUiStateFlow(tabKey: String): StateFlow<ThreadUiState> {
        val viewModel = threadViewModelFor(tabKey)
        preparePostDialogIdentityHistory(tabKey, viewModel)
        return tabSessionStore.openThreadTabs
            .map { tabs -> tabs.find { tab -> tab.id.value == tabKey } }
            .flatMapLatest { tab ->
                // --- Initialization ---
                initializeThreadViewModel(viewModel, tab)

                // --- Output ---
                if (tab == null) {
                    flowOf(ThreadUiState())
                } else {
                    viewModel.uiState
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
                initialValue = viewModel.uiState.value,
            )
    }

    /**
     * boardId が確定したタイミングで、新しい holder 側の投稿ダイアログに履歴監視を準備する。
     */
    private fun preparePostDialogIdentityHistory(tabKey: String, viewModel: ThreadViewModel) {
        viewModelScope.launch {
            viewModel.uiState
                .map { it.boardInfo.boardId }
                .distinctUntilChanged()
                .filter { it != 0L }
                .take(1)
                .collect { boardId ->
                    tabSessionStore.threadPostDialogController(tabKey).prepareIdentityHistory(boardId)
                }
        }
    }

    /**
     * 開いているタブ全ての投稿成功イベントを監視し、対象タブの後処理を行う。
     */
    private fun attachPostSuccessCollectors(tabs: List<ThreadTabInfo>) {
        val currentKeys = tabs.map { it.id.value }.toSet()
        postSuccessCollectJobs.keys.filterNot { it in currentKeys }.forEach { key ->
            postSuccessCollectJobs.remove(key)?.cancel()
        }
        tabs.forEach { tab ->
            if (postSuccessCollectJobs.containsKey(tab.id.value)) {
                return@forEach
            }
            postSuccessCollectJobs[tab.id.value] = viewModelScope.launch {
                tabSessionStore.threadPostDialogSuccessEvents(tab.id.value)
                    .collect { success ->
                        onThreadPostSuccess(tab.id.value, success)
                    }
            }
        }
    }

    /**
     * 投稿成功時に、対象タブの runtime state を更新してから再読み込みを行う。
     */
    private fun onThreadPostSuccess(tabKey: String, success: PostDialogSuccess) {
        val threadId = ThreadId(tabKey)
        tabSessionStore.updateThreadRuntimeState(threadId) { current ->
            current.copy(
                pendingPost = PendingThreadPostState(
                    resNum = success.resNum,
                    content = success.message,
                    name = success.name,
                    email = success.mail,
                )
            )
        }
        reloadThread(tabKey)
    }

    /**
     * タブ情報がある場合だけ既存スレッド ViewModel を初期化する。
     */
    private fun initializeThreadViewModel(
        viewModel: ThreadViewModel,
        tab: ThreadTabInfo?,
    ) {
        if (tab == null) {
            return
        }
        viewModel.initializeThread(
            threadKey = tab.threadKey,
            boardInfo = com.websarva.wings.android.slevo.data.model.BoardInfo(
                name = tab.boardName,
                url = tab.boardUrl,
                boardId = tab.boardId,
            ),
            threadTitle = tab.title,
        )
    }

    /**
     * 現在の open tabs から対象タブ情報を取得する。
     */
    private fun findTab(tabKey: String): ThreadTabInfo? {
        return tabSessionStore.openThreadTabs.value.find { tab -> tab.id.value == tabKey }
    }

    /**
     * 開いているタブ一覧から外れたキャッシュだけを解放する。
     */
    private fun evictClosedTabs(openKeys: Set<String>) {
        val removedKeys = viewModelCache.keys.filterNot(openKeys::contains)
        removedKeys.forEach { key ->
            viewModelCache.remove(key)?.disposeResources()
            uiStateCache.remove(key)
        }
    }

    /**
     * route ViewModel 終了時に内部キャッシュの旧 ViewModel を解放する。
     */
    override fun onCleared() {
        viewModelCache.values.forEach { it.disposeResources() }
        viewModelCache.clear()
        uiStateCache.clear()
        super.onCleared()
    }

    /**
     * 選択中タブ key を更新する。
     */
    fun selectTab(threadId: ThreadId?) {
        tabSessionStore.selectThreadTab(threadId)
    }
}
