package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.common.bookmark.ThreadTarget
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogSuccess
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.PendingThreadPostState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadLoadingSource
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.distinctImageUrls
import com.websarva.wings.android.slevo.ui.util.extractImageUrls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * スレッド画面 route 単位でタブ表示状態を直接合成する ViewModel。
 *
 * タブごとの SessionState、runtime state、設定、ブックマーク、NG、履歴、dat 取得結果を結合し、
 * `ThreadUiState` を route レベルで遅延生成する。
 */
@HiltViewModel
class ThreadRouteViewModel @Inject constructor(
    private val tabSessionStore: TabSessionStore,
    private val boardRepository: BoardRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val postHistoryRepository: PostHistoryRepository,
    private val threadBookmarkRepository: ThreadBookmarkRepository,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    private val tabsRepository: TabsRepository,
    private val threadReadStateRepository: ThreadReadStateRepository,
    private val threadContentLoadUseCase: ThreadContentLoadUseCase,
    private val threadVisiblePostsUseCase: ThreadVisiblePostsUseCase,
    private val logger: AppLogger,
) : ViewModel() {

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
        const val AUTO_REFRESH_INTERVAL_MILLIS = 10_000L
    }

    private val tabCoordinator = ThreadTabCoordinator(
        scope = viewModelScope,
        tabsRepository = tabsRepository,
        readStateRepository = threadReadStateRepository,
    )

    private val uiStateCache = mutableMapOf<String, StateFlow<ThreadUiState>>()
    private val contentStates = MutableStateFlow<Map<String, ThreadRouteContentState>>(emptyMap())
    private val initializationJobs = mutableMapOf<String, Job>()
    private val threadLoadJobs = mutableMapOf<String, Job>()
    private val postSuccessCollectJobs = mutableMapOf<String, Job>()
    private val myPostCollectJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            tabSessionStore.openThreadTabs.collect { tabs ->
                evictClosedTabs(tabs.map { tab -> tab.id.value }.toSet())
                attachPostSuccessCollectors(tabs)
            }
        }
    }

    /** 現在選択中のスレッドタブ key。 */
    val selectedTabKey: StateFlow<String?> = tabSessionStore.selectedThreadTabKey

    /**
     * 指定タブ key の `UiState` Flow を返す。
     */
    fun uiStateFor(tabKey: String): StateFlow<ThreadUiState> {
        return uiStateCache.getOrPut(tabKey) { createUiStateFlow(tabKey) }
    }

    /** 指定スレッドタブのブックマークシート holder を返す。 */
    fun bookmarkSheetHolderFor(tabKey: String): BookmarkBottomSheetStateHolder =
        tabSessionStore.threadBookmarkSheetHolder(tabKey)

    /** 指定スレッドタブの投稿ダイアログコントローラを返す。 */
    fun postDialogActionsFor(tabKey: String): PostDialogController =
        tabSessionStore.threadPostDialogController(tabKey)

    /** 指定スレッドタブの画像保存イベント Flow を返す。 */
    fun imageSaveEventsFor(tabKey: String): SharedFlow<ImageSaveUiEvent> =
        tabSessionStore.threadImageSaveEvents(tabKey)

    /** 指定スレッドタブの画像保存要求を処理する。 */
    fun requestImageSave(tabKey: String, context: android.content.Context, urls: List<String>) {
        tabSessionStore.threadRequestImageSave(tabKey, context, urls)
    }

    /** 指定スレッドタブの画像保存権限要求結果を処理する。 */
    fun onImageSavePermissionResult(
        tabKey: String,
        context: android.content.Context,
        granted: Boolean
    ) {
        tabSessionStore.threadOnImageSavePermissionResult(tabKey, context, granted)
    }

    /** 指定スレッドタブの投稿ダイアログに画像をアップロードする。 */
    fun uploadPostDialogImage(
        tabKey: String,
        context: android.content.Context,
        uri: android.net.Uri
    ) {
        tabSessionStore.threadUploadPostDialogImage(tabKey, context, uri)
    }

    /** 指定スレッドタブのブックマークシートを開く。 */
    fun openBookmarkSheet(tabKey: String) {
        val state = uiStateFor(tabKey).value
        val boardInfo = state.boardInfo
        val threadInfo = state.threadInfo
        if (boardInfo.url.isBlank() || threadInfo.key.isBlank()) {
            return
        }
        tabSessionStore.threadBookmarkSheetHolder(tabKey).open(
            listOf(
                ThreadTarget(
                    boardInfo = boardInfo,
                    threadInfo = threadInfo,
                    currentGroupId = state.bookmarkStatusState.selectedGroup?.id,
                )
            )
        )
    }

    /** 指定スレッドタブを再読み込みする。 */
    fun reloadThread(tabKey: String) {
        launchThreadLoad(tabKey, ThreadLoadingSource.MANUAL)
    }

    /** 下端プル更新を実行する。 */
    fun reloadThreadFromBottomPull(tabKey: String) {
        launchThreadLoad(tabKey, ThreadLoadingSource.BOTTOM_PULL)
    }

    /** 表示中タブだけ自動更新する。 */
    fun onAutoScrollReachedBottom(tabKey: String) {
        if (selectedTabKey.value != tabKey) {
            return
        }
        val state = uiStateFor(tabKey).value
        if (!state.isAutoScroll) {
            return
        }
        val threadId = ThreadId(tabKey)
        val now = System.currentTimeMillis()
        val runtime = tabSessionStore.getThreadRuntimeState(threadId)
        if (runtime.lastAutoRefreshTime != 0L && now - runtime.lastAutoRefreshTime < AUTO_REFRESH_INTERVAL_MILLIS) {
            return
        }
        tabSessionStore.updateThreadRuntimeState(threadId) { it.copy(lastAutoRefreshTime = now) }
        launchThreadLoad(tabKey, ThreadLoadingSource.AUTO_SCROLL)
    }

    /** スクロール位置を保存する。 */
    fun updateThreadScrollPosition(threadId: ThreadId, firstVisibleIndex: Int, scrollOffset: Int) {
        tabCoordinator.updateThreadScrollPosition(threadId, firstVisibleIndex, scrollOffset)
    }

    /** タブのタイトルとレス数を同期する。 */
    fun updateThreadTabInfo(threadId: ThreadId, title: String, resCount: Int) {
        tabCoordinator.updateThreadTabInfo(threadId, title, resCount)
    }

    /** 既読位置を保存する。 */
    fun updateThreadLastRead(threadId: ThreadId, lastReadResNo: Int) {
        tabCoordinator.updateThreadLastRead(threadId, lastReadResNo)
    }

    /** タブ由来の新着境界を現在の表示状態へ反映する。 */
    fun setNewArrivalInfo(tabKey: String, firstNewResNo: Int?, prevResCount: Int) {
        updateContentState(tabKey) { current ->
            current.copy(
                firstNewResNo = firstNewResNo,
                prevResCount = prevResCount
            )
        }
    }

    /** 検索バーを開く。 */
    fun startSearch(tabKey: String) {
        updateThreadSessionState(tabKey) { state ->
            val next = state.copy(isSearchMode = true)
            next.copy(isTabSwipeEnabled = shouldEnableTabSwipe(next))
        }
    }

    /** 検索バーを閉じる。 */
    fun closeSearch(tabKey: String) {
        updateThreadSessionState(tabKey) { state ->
            val next = state.copy(isSearchMode = false, searchInputValue = TextFieldValue(""))
            next.copy(isTabSwipeEnabled = shouldEnableTabSwipe(next))
        }
    }

    /** 検索入力状態を更新する。 */
    fun updateSearchInput(tabKey: String, inputValue: TextFieldValue) {
        updateThreadSessionState(tabKey) { it.copy(searchInputValue = inputValue) }
    }

    /** ソート種別を切り替える。 */
    fun toggleSortType(tabKey: String) {
        updateThreadSessionState(tabKey) { state ->
            state.copy(
                sortType = if (state.sortType == ThreadSortType.NUMBER) ThreadSortType.TREE else ThreadSortType.NUMBER,
            )
        }
    }

    /** 自動スクロールの有効状態を切り替える。 */
    fun toggleAutoScroll(tabKey: String) {
        val threadId = ThreadId(tabKey)
        val enabled = !tabSessionStore.getThreadSessionState(threadId).isAutoScroll
        updateThreadSessionState(tabKey) { it.copy(isAutoScroll = enabled) }
        if (!enabled) {
            tabSessionStore.updateThreadRuntimeState(threadId) { it.copy(lastAutoRefreshTime = 0L) }
        }
    }

    /** スレ情報シートを開く。 */
    fun openThreadInfoSheet(tabKey: String) {
        updateThreadSessionState(tabKey) { it.copy(showThreadInfoSheet = true) }
    }

    /** スレ情報シートを閉じる。 */
    fun closeThreadInfoSheet(tabKey: String) {
        updateThreadSessionState(tabKey) { it.copy(showThreadInfoSheet = false) }
    }

    /** More シートを開く。 */
    fun openMoreSheet(tabKey: String) {
        updateThreadSessionState(tabKey) { it.copy(showMoreSheet = true) }
    }

    /** More シートを閉じる。 */
    fun closeMoreSheet(tabKey: String) {
        updateThreadSessionState(tabKey) { it.copy(showMoreSheet = false) }
    }

    /** 表示設定シートを開く。 */
    fun openDisplaySettingsSheet(tabKey: String) {
        updateThreadSessionState(tabKey) { it.copy(showDisplaySettingsSheet = true) }
    }

    /** 表示設定シートを閉じる。 */
    fun closeDisplaySettingsSheet(tabKey: String) {
        updateThreadSessionState(tabKey) { it.copy(showDisplaySettingsSheet = false) }
    }

    /** 画像メニューを開く。 */
    fun openImageMenu(tabKey: String, url: String, imageUrls: List<String>) {
        if (url.isBlank()) {
            return
        }
        val menuUrls = buildImageMenuUrls(url, imageUrls)
        updateThreadSessionState(tabKey) {
            it.copy(
                showImageMenuSheet = true,
                imageMenuTargetUrl = url,
                imageMenuTargetUrls = menuUrls,
            )
        }
    }

    /** 画像メニューを閉じる。 */
    fun closeImageMenu(tabKey: String) {
        updateThreadSessionState(tabKey) {
            it.copy(
                showImageMenuSheet = false,
                imageMenuTargetUrl = null,
                imageMenuTargetUrls = emptyList()
            )
        }
    }

    /** 画像 NG ダイアログを開く。 */
    fun openImageNgDialog(tabKey: String, url: String) {
        if (url.isBlank()) {
            return
        }
        updateThreadSessionState(tabKey) {
            it.copy(
                showImageNgDialog = true,
                imageNgTargetUrl = url
            )
        }
    }

    /** 画像 NG ダイアログを閉じる。 */
    fun closeImageNgDialog(tabKey: String) {
        updateThreadSessionState(tabKey) {
            it.copy(
                showImageNgDialog = false,
                imageNgTargetUrl = null
            )
        }
    }

    /** 画像読み込み開始を反映する。 */
    fun onThreadImageLoadStart(tabKey: String, imageUrl: String) {
        if (imageUrl.isBlank()) {
            return
        }
        updateContentState(tabKey) { state -> state.copy(imageLoadingUrls = state.imageLoadingUrls + imageUrl) }
    }

    /** 画像読み込み失敗を反映する。 */
    fun onThreadImageLoadError(
        tabKey: String,
        imageUrl: String,
        failureType: ImageLoadFailureType
    ) {
        if (imageUrl.isBlank()) {
            return
        }
        updateContentState(tabKey) { state ->
            state.copy(
                imageLoadFailureByUrl = state.imageLoadFailureByUrl + (imageUrl to failureType),
                imageLoadingUrls = state.imageLoadingUrls - imageUrl,
            )
        }
    }

    /** 画像読み込み成功を反映する。 */
    fun onThreadImageLoadSuccess(tabKey: String, imageUrl: String) {
        if (imageUrl.isBlank()) {
            return
        }
        updateContentState(tabKey) { state ->
            state.copy(
                imageLoadFailureByUrl = state.imageLoadFailureByUrl - imageUrl,
                imageLoadingUrls = state.imageLoadingUrls - imageUrl,
            )
        }
    }

    /** 画像読み込みリトライ時に失敗状態を解除する。 */
    fun onThreadImageRetry(tabKey: String, imageUrl: String) {
        if (imageUrl.isBlank()) {
            return
        }
        updateContentState(tabKey) { state ->
            state.copy(
                imageLoadFailureByUrl = state.imageLoadFailureByUrl - imageUrl,
                imageLoadingUrls = state.imageLoadingUrls - imageUrl,
            )
        }
    }

    /** ポップアップのサイズを更新する。 */
    fun updatePopupSize(tabKey: String, index: Int, size: IntSize) {
        updateThreadSessionState(tabKey) { state ->
            val stack = state.popupStack
            if (index !in stack.indices) {
                return@updateThreadSessionState state
            }
            val target = stack[index]
            if (target.size == size) {
                return@updateThreadSessionState state
            }
            val updated = stack.toMutableList()
            updated[index] = target.copy(size = size)
            val next = state.copy(popupStack = updated)
            next.copy(isTabSwipeEnabled = shouldEnableTabSwipe(next))
        }
    }

    /** 最上位ポップアップを閉じる。 */
    fun removeTopPopup(tabKey: String) {
        updateThreadSessionState(tabKey) { state ->
            if (state.popupStack.isEmpty()) {
                return@updateThreadSessionState state
            }
            val next = state.copy(popupStack = state.popupStack.dropLast(1))
            next.copy(isTabSwipeEnabled = shouldEnableTabSwipe(next))
        }
    }

    /** 返信元レス群をポップアップへ追加する。 */
    fun addPopupForReplyFrom(tabKey: String, baseOffset: IntOffset, replyNumbers: List<Int>) {
        val state = uiStateFor(tabKey).value
        val posts = state.posts ?: return
        val targetNumbers =
            replyNumbers.filterNot { it in state.ngPostNumbers }.filter { it in 1..posts.size }
        if (targetNumbers.isEmpty()) {
            return
        }
        appendPopup(
            tabKey = tabKey,
            info = PopupInfo(
                popupId = nextPopupId(tabKey),
                postNumbers = targetNumbers,
                offset = baseOffset,
                rootNumbers = targetNumbers.map { num -> state.treeRootMap[num] ?: num },
            ),
        )
    }

    /** 単一レスをポップアップへ追加する。 */
    fun addPopupForReplyNumber(tabKey: String, baseOffset: IntOffset, postNumber: Int) {
        val state = uiStateFor(tabKey).value
        val posts = state.posts ?: return
        if (postNumber !in 1..posts.size || postNumber in state.ngPostNumbers) {
            return
        }
        appendPopup(
            tabKey = tabKey,
            info = PopupInfo(
                popupId = nextPopupId(tabKey),
                postNumbers = listOf(postNumber),
                offset = baseOffset,
                rootNumbers = listOf(postNumber),
            ),
        )
    }

    /** 指定 ID のレス群をポップアップへ追加する。 */
    fun addPopupForId(tabKey: String, baseOffset: IntOffset, id: String) {
        val state = uiStateFor(tabKey).value
        val posts = state.posts ?: return
        val targetNumbers = posts.mapIndexedNotNull { index, post ->
            val num = index + 1
            if (post.header.id == id && num !in state.ngPostNumbers) num else null
        }
        if (targetNumbers.isEmpty()) {
            return
        }
        appendPopup(
            tabKey = tabKey,
            info = PopupInfo(
                popupId = nextPopupId(tabKey),
                postNumbers = targetNumbers,
                offset = baseOffset,
                rootNumbers = targetNumbers.map { num -> state.treeRootMap[num] ?: num },
            ),
        )
    }

    /** 指定レスの属するツリーをポップアップへ追加する。 */
    fun addPopupForTree(tabKey: String, baseOffset: IntOffset, postNumber: Int) {
        val state = uiStateFor(tabKey).value
        val posts = state.posts ?: return
        val selection =
            deriveTreePopupSelection(postNumber, state.treeOrder, state.treeDepthMap) ?: return
        val postNumbers = mutableListOf<Int>()
        val indentLevels = mutableListOf<Int>()
        val rootNumbers = mutableListOf<Int>()
        selection.numbers.zip(selection.indentLevels).forEach { (num, depth) ->
            if (num in state.ngPostNumbers) {
                return@forEach
            }
            posts.getOrNull(num - 1) ?: return@forEach
            postNumbers += num
            indentLevels += depth
            rootNumbers += state.treeRootMap[num] ?: num
        }
        if (postNumbers.size <= 1) {
            return
        }
        appendPopup(
            tabKey = tabKey,
            info = PopupInfo(
                popupId = nextPopupId(tabKey),
                postNumbers = postNumbers,
                offset = baseOffset,
                indentLevels = indentLevels,
                rootNumbers = rootNumbers,
            ),
        )
    }

    /** Toast 消費済みにする。 */
    fun consumeToast(tabKey: String) {
        updateThreadSessionState(tabKey) { it.copy(pendingToastResId = null) }
    }

    /** テキスト倍率を更新する。 */
    fun updateTextScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setTextScale(scale)
            if (!settingsRepository.observeIsIndividualTextScale().first()) {
                settingsRepository.setBodyTextScale(scale)
                settingsRepository.setHeaderTextScale(scale * 0.85f)
            }
        }
    }

    /** 個別倍率設定の有効状態を更新する。 */
    fun updateIndividualTextScale(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIndividualTextScale(enabled)
            if (!enabled) {
                settingsRepository.setLineHeight(TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT)
            }
        }
    }

    /** ヘッダ倍率を更新する。 */
    fun updateHeaderTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setHeaderTextScale(scale) }
    }

    /** 本文倍率を更新する。 */
    fun updateBodyTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setBodyTextScale(scale) }
    }

    /** 行間を更新する。 */
    fun updateLineHeight(height: Float) {
        viewModelScope.launch { settingsRepository.setLineHeight(height) }
    }

    /** route ViewModel の内部ジョブを解放する。 */
    override fun onCleared() {
        initializationJobs.values.forEach(Job::cancel)
        threadLoadJobs.values.forEach(Job::cancel)
        postSuccessCollectJobs.values.forEach(Job::cancel)
        myPostCollectJobs.values.forEach(Job::cancel)
        initializationJobs.clear()
        threadLoadJobs.clear()
        postSuccessCollectJobs.clear()
        myPostCollectJobs.clear()
        uiStateCache.clear()
        contentStates.value = emptyMap()
        super.onCleared()
    }

    /** タブごとの共有 `UiState` Flow を組み立てる。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createUiStateFlow(tabKey: String): StateFlow<ThreadUiState> {
        val textSettingsFlow = combine(
            settingsRepository.observeTextScale(),
            settingsRepository.observeIsIndividualTextScale(),
            settingsRepository.observeHeaderTextScale(),
            settingsRepository.observeBodyTextScale(),
            settingsRepository.observeLineHeight(),
        ) { textScale, isIndividual, headerScale, bodyScale, lineHeight ->
            ThreadRouteTextSettingsState(
                textScale = textScale,
                isIndividualTextScale = isIndividual,
                headerTextScale = headerScale,
                bodyTextScale = bodyScale,
                lineHeight = lineHeight,
            )
        }
        val settingsFlow = combine(
            textSettingsFlow,
            settingsRepository.observeIsThreadMinimapScrollbarEnabled(),
            settingsRepository.observeGestureSettings(),
        ) { textSettings, showMinimap, gestureSettings ->
            ThreadRouteSettingsState(
                textScale = textSettings.textScale,
                isIndividualTextScale = textSettings.isIndividualTextScale,
                headerTextScale = textSettings.headerTextScale,
                bodyTextScale = textSettings.bodyTextScale,
                lineHeight = textSettings.lineHeight,
                showMinimapScrollbar = showMinimap,
                gestureSettings = gestureSettings,
            )
        }
        val tabFlow =
            tabSessionStore.openThreadTabs
                .map { tabs -> tabs.find { it.id.value == tabKey } }
                .distinctUntilChangedBy { tab -> tab?.toUiStateSourceKey() }
        val sessionFlow = tabSessionStore.threadSessionStates.map { states ->
            states[tabKey] ?: ThreadSessionState()
        }
        val contentFlow =
            contentStates.map { states -> states[tabKey] ?: ThreadRouteContentState() }
        val bookmarkSheetStateFlow = tabSessionStore.threadBookmarkSheetHolder(tabKey).uiState
        val bookmarkStatusFlow = tabFlow.flatMapLatest { tab ->
            if (tab == null) {
                flowOf(BookmarkStatusState())
            } else {
                threadBookmarkRepository.getBookmarkWithGroup(tab.threadKey, tab.boardUrl)
                    .map { threadWithBookmark ->
                        val group = threadWithBookmark?.group
                        BookmarkStatusState(isBookmarked = group != null, selectedGroup = group)
                    }
            }
        }
        val ngFlow = ngRepository.observeNgs()

        val baseUiStateFlow = combine(
            tabFlow,
            sessionFlow,
            contentFlow,
            settingsFlow,
            bookmarkStatusFlow,
        ) { tab, session, content, settings, bookmarkStatus ->
            ThreadRouteBaseUiStateInput(
                tab = tab,
                session = session,
                content = content,
                settings = settings,
                bookmarkStatus = bookmarkStatus,
            )
        }

        return combine(
            baseUiStateFlow,
            bookmarkSheetStateFlow,
            ngFlow
        ) { baseInput, bookmarkSheetState, ngList ->
            val tab = baseInput.tab ?: return@combine ThreadUiState()
            composeThreadUiState(
                tab = tab,
                session = baseInput.session,
                content = baseInput.content,
                settings = baseInput.settings,
                bookmarkStatus = baseInput.bookmarkStatus,
                bookmarkSheetState = bookmarkSheetState,
                ngList = ngList,
            )
        }
            .onStart {
                // --- Lazy initialization ---
                // 実際に購読されたタブだけ metadata 補完と初回ロードを開始する。
                ensureTabInitialized(tabKey)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
                initialValue = ThreadUiState(),
            )
    }

    /** 指定 tab key の初期化を必要時だけ開始する。 */
    private fun ensureTabInitialized(tabKey: String) {
        val tab = tabSessionStore.openThreadTabs.value.find { it.id.value == tabKey } ?: return
        ensureTabInitialized(tab)
    }

    /** 初期化未実行タブの board 情報補完と初回ロードを開始する。 */
    private fun ensureTabInitialized(tab: ThreadTabInfo) {
        if (initializationJobs.containsKey(tab.id.value)) {
            return
        }
        initializationJobs[tab.id.value] = viewModelScope.launch {
            initializeTabMetadata(tab)
            if (tabSessionStore.getThreadSessionState(tab.id).loadingSource == ThreadLoadingSource.NONE) {
                val isTree = settingsRepository.observeIsTreeSort().first()
                updateThreadSessionState(tab.id.value) { state ->
                    state.copy(sortType = if (isTree) ThreadSortType.TREE else ThreadSortType.NUMBER)
                }
                launchThreadLoad(tab.id.value, ThreadLoadingSource.INITIAL)
            }
        }
    }

    /** 指定スレッドタブの読み込みジョブを 1 本だけ動かす。 */
    private fun launchThreadLoad(tabKey: String, source: ThreadLoadingSource) {
        threadLoadJobs.remove(tabKey)?.cancel()
        startThreadLoad(tabKey, source)
        val job = viewModelScope.launch {
            try {
                loadThreadContent(tabKey)
            } finally {
                if (threadLoadJobs[tabKey] === this.coroutineContext[Job]) {
                    threadLoadJobs.remove(tabKey)
                }
            }
        }
        threadLoadJobs[tabKey] = job
    }

    /** タブ metadata と投稿ダイアログ初期値を補完する。 */
    private suspend fun initializeTabMetadata(tab: ThreadTabInfo) {
        val ensuredBoardId = boardRepository.ensureBoard(
            BoardInfo(
                tab.boardId,
                tab.boardName,
                tab.boardUrl
            )
        )
        tabSessionStore.updateThreadResolvedBoardInfo(
            threadId = tab.id,
            boardId = ensuredBoardId,
            boardName = tab.boardName,
        )
        val boardInfo = BoardInfo(
            boardId = ensuredBoardId,
            name = tab.boardName,
            url = tab.boardUrl,
        )
        val threadInfo = ThreadInfo(
            key = tab.threadKey,
            title = buildInitialThreadTitle(tab.boardUrl, tab.threadKey, tab.title),
            url = tab.boardUrl,
            resCount = tab.resCount,
        )
        updateContentState(tab.id.value) {
            val initialUnreadStartResNo = if (it.initialUnreadBoundaryInitialized) {
                it.initialUnreadStartResNo
            } else {
                tab.newResCount.takeIf { newResCount -> newResCount > 0 }
                    ?.let { tab.lastReadResNo + 1 }
            }
            it.copy(
                boardInfo = boardInfo,
                threadInfo = threadInfo,
                firstNewResNo = tab.firstNewResNo,
                prevResCount = tab.prevResCount,
                initialUnreadStartResNo = initialUnreadStartResNo,
                initialUnreadBoundaryInitialized = true,
            )
        }
        boardRepository.fetchBoardNoname("${tab.boardUrl}SETTING.TXT")?.let { noname ->
            updateContentState(tab.id.value) { state ->
                state.copy(boardInfo = state.boardInfo.copy(noname = noname))
            }
            updateThreadSessionState(tab.id.value) { state ->
                state.copy(postDialogState = state.postDialogState.copy(namePlaceholder = noname))
            }
        }
        tabSessionStore.threadPostDialogController(tab.id.value)
            .prepareIdentityHistory(boardInfo.boardId)
    }

    /** 読み込み開始状態を SessionState に反映する。 */
    private fun startThreadLoad(tabKey: String, source: ThreadLoadingSource) {
        updateThreadSessionState(tabKey) {
            it.copy(isLoading = true, loadProgress = 0f, loadingSource = source)
        }
    }

    /** dat 読み込みと派生状態更新を実行する。 */
    private suspend fun loadThreadContent(tabKey: String) {
        // --- Guard ---
        val tab = tabSessionStore.openThreadTabs.value.find { it.id.value == tabKey } ?: return
        val content = contentStates.value[tabKey] ?: ThreadRouteContentState(
            boardInfo = BoardInfo(tab.boardId, tab.boardName, tab.boardUrl),
            threadInfo = ThreadInfo(key = tab.threadKey, title = tab.title, url = tab.boardUrl),
        )
        try {
            // --- Load ---
            val derived = threadContentLoadUseCase.load(tab.boardUrl, tab.threadKey) { progress ->
                updateThreadSessionState(tabKey) { it.copy(loadProgress = progress) }
            }
            if (derived == null) {
                handleLoadFailure(tabKey, tab.boardUrl, tab.threadKey)
                return
            }
            if (!isThreadTabOpen(tabKey)) return

            // --- Persistence / derived state update ---
            applyLoadSuccess(tabKey, content, derived)
            val nextContent = contentStates.value[tabKey] ?: ThreadRouteContentState()
            val historyId = historyRepository.recordHistory(
                boardInfo = nextContent.boardInfo,
                threadInfo = nextContent.threadInfo,
                resCount = derived.uiPosts.size,
            )
            collectMyPostNumbers(tabKey, historyId)
            recordPendingPost(tabKey, derived.uiPosts, historyId)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            handleLoadFailure(tabKey, tab.boardUrl, tab.threadKey, error)
        }
    }

    /** 読み込み成功後の表示状態を更新する。 */
    private fun applyLoadSuccess(
        tabKey: String,
        previous: ThreadRouteContentState,
        derived: ThreadContentLoadResult,
    ) {
        val activeImageUrls = deriveActiveImageUrls(derived.uiPosts)
        val prunedPopupStack = prunePopupStackWithoutRenderablePosts(
            popupStack = tabSessionStore.getThreadSessionState(ThreadId(tabKey)).popupStack,
            posts = derived.uiPosts,
            ngPostNumbers = emptySet(),
        )
        updateThreadSessionState(tabKey) { session ->
            val next = session.copy(
                isLoading = false,
                loadProgress = 1f,
                loadingSource = ThreadLoadingSource.NONE,
                popupStack = prunedPopupStack,
            )
            next.copy(isTabSwipeEnabled = shouldEnableTabSwipe(next))
        }
        updateContentState(tabKey) { current ->
            val groups = updatePostGroups(
                previousGroups = previous.postGroups,
                previousResCount = previous.lastLoadedResCount,
                posts = derived.uiPosts,
                initialUnreadStartResNo = previous.initialUnreadStartResNo,
                isInitialLoad = previous.posts == null,
            )
            current.copy(
                posts = derived.uiPosts,
                threadInfo = (if (current.threadInfo.key.isBlank()) previous.threadInfo else current.threadInfo).copy(
                    title = derived.threadTitle
                        ?: current.threadInfo.title.ifBlank { previous.threadInfo.title },
                    key = current.threadInfo.key.ifBlank { previous.threadInfo.key },
                    url = current.threadInfo.url.ifBlank { previous.threadInfo.url },
                    resCount = derived.resCount,
                    date = derived.threadDate,
                    momentum = derived.momentum,
                ),
                idCountMap = derived.idCountMap,
                idIndexList = derived.idIndexList,
                replySourceMap = derived.replySourceMap,
                treeOrder = derived.treeOrder,
                treeDepthMap = derived.treeDepthMap,
                treeRootMap = derived.treeRootMap,
                imageLoadFailureByUrl = current.imageLoadFailureByUrl.filterKeys { it in activeImageUrls },
                imageLoadingUrls = current.imageLoadingUrls.filter { it in activeImageUrls }
                    .toSet(),
                postGroups = groups.groups,
                lastLoadedResCount = groups.lastLoadedResCount,
                latestArrivalGroupIndex = groups.latestArrivalGroupIndex,
                firstNewResNo = current.firstNewResNo,
                prevResCount = current.prevResCount,
            )
        }
    }

    /** 失敗時にローディング解除と toast を反映する。 */
    private fun handleLoadFailure(
        tabKey: String,
        boardUrl: String,
        threadKey: String,
        error: Throwable? = null
    ) {
        updateThreadSessionState(tabKey) {
            it.copy(isLoading = false, loadProgress = 1f, loadingSource = ThreadLoadingSource.NONE)
        }
        if (error != null) {
            logger.e(
                message = "Failed to load thread data for board: $boardUrl key: $threadKey",
                throwable = error
            )
        }
        updateThreadSessionState(tabKey) { it.copy(pendingToastResId = R.string.thread_load_failed) }
    }

    /** 投稿成功イベントを各タブへ紐付ける。 */
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
                tabSessionStore.threadPostDialogSuccessEvents(tab.id.value).collect { success ->
                    onThreadPostSuccess(tab.id.value, success)
                }
            }
        }
    }

    /** 投稿成功後の pending post 記録と再読み込みを行う。 */
    private fun onThreadPostSuccess(tabKey: String, success: PostDialogSuccess) {
        tabSessionStore.updateThreadRuntimeState(ThreadId(tabKey)) { current ->
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

    /** 自分の投稿番号監視を履歴 ID 単位で差し替える。 */
    private fun collectMyPostNumbers(tabKey: String, historyId: Long) {
        val current = contentStates.value[tabKey]
        if (current?.observedThreadHistoryId == historyId) {
            return
        }
        myPostCollectJobs.remove(tabKey)?.cancel()
        updateContentState(tabKey) { it.copy(observedThreadHistoryId = historyId) }
        myPostCollectJobs[tabKey] = viewModelScope.launch {
            postHistoryRepository.observeMyPostNumbers(historyId).collect { nums ->
                updateContentState(tabKey) { state -> state.copy(myPostNumbers = nums) }
            }
        }
    }

    /** 保留投稿があれば履歴へ保存して消費する。 */
    private suspend fun recordPendingPost(
        tabKey: String,
        uiPosts: List<ThreadPostUiModel>,
        historyId: Long
    ) {
        val threadId = ThreadId(tabKey)
        val pending = tabSessionStore.getThreadRuntimeState(threadId).pendingPost ?: return
        val boardInfo = (contentStates.value[tabKey] ?: ThreadRouteContentState()).boardInfo
        val resNumber = pending.resNum ?: uiPosts.size
        if (resNumber in 1..uiPosts.size) {
            val post = uiPosts[resNumber - 1]
            postHistoryRepository.recordPost(
                content = pending.content,
                date = parseDateToUnix(post.header.date),
                threadHistoryId = historyId,
                boardId = boardInfo.boardId,
                resNum = resNumber,
                name = pending.name,
                email = pending.email,
                postId = post.header.id,
            )
        }
        tabSessionStore.updateThreadRuntimeState(threadId) { it.copy(pendingPost = null) }
    }

    /** ThreadUiState を各入力から直接合成する。 */
    private fun composeThreadUiState(
        tab: ThreadTabInfo,
        session: ThreadSessionState,
        content: ThreadRouteContentState,
        settings: ThreadRouteSettingsState,
        bookmarkStatus: BookmarkStatusState,
        bookmarkSheetState: BookmarkSheetUiState,
        ngList: List<NgEntity>,
    ): ThreadUiState {
        val posts = content.posts
        val compiledNg = compileNgs(ngList)
        val ngPostNumbers = if (posts == null) {
            emptySet()
        } else {
            deriveNgPostNumbers(posts, content.boardInfo.boardId, compiledNg)
        }
        val popupStack = if (posts == null) {
            session.popupStack
        } else {
            prunePopupStackWithoutRenderablePosts(session.popupStack, posts, ngPostNumbers)
        }
        val visibleRowsResult = if (posts == null) {
            null
        } else {
            threadVisiblePostsUseCase.buildVisibleRows(
                posts = posts,
                groups = content.postGroups,
                sortType = session.sortType,
                treeOrder = content.treeOrder,
                treeDepthMap = content.treeDepthMap,
                treeRootMap = content.treeRootMap,
                latestArrivalGroupIndex = content.latestArrivalGroupIndex,
                searchQuery = session.searchQuery,
                ngPostNumbers = ngPostNumbers,
                replySourceMap = content.replySourceMap,
            )
        }
        val boardInfo = content.boardInfo.takeIf { it.url.isNotBlank() }
            ?: BoardInfo(tab.boardId, tab.boardName, tab.boardUrl)
        val threadInfo = content.threadInfo.takeIf { it.key.isNotBlank() }
            ?: ThreadInfo(
                key = tab.threadKey,
                title = buildInitialThreadTitle(tab.boardUrl, tab.threadKey, tab.title),
                url = tab.boardUrl,
                resCount = tab.resCount,
            )
        return ThreadUiState(
            threadInfo = threadInfo.copy(resCount = maxOf(threadInfo.resCount, tab.resCount)),
            posts = posts,
            loadProgress = session.loadProgress,
            boardInfo = boardInfo,
            bookmarkStatusState = bookmarkStatus,
            bookmarkSheetState = bookmarkSheetState,
            isLoading = session.isLoading,
            loadingSource = session.loadingSource,
            postDialogState = session.postDialogState,
            showThreadInfoSheet = session.showThreadInfoSheet,
            showMoreSheet = session.showMoreSheet,
            showDisplaySettingsSheet = session.showDisplaySettingsSheet,
            showImageMenuSheet = session.showImageMenuSheet,
            imageMenuTargetUrl = session.imageMenuTargetUrl,
            imageMenuTargetUrls = session.imageMenuTargetUrls,
            showImageNgDialog = session.showImageNgDialog,
            imageNgTargetUrl = session.imageNgTargetUrl,
            popupStack = popupStack,
            myPostNumbers = content.myPostNumbers,
            idCountMap = content.idCountMap,
            idIndexList = content.idIndexList,
            replySourceMap = content.replySourceMap,
            ngPostNumbers = ngPostNumbers,
            imageLoadFailureByUrl = content.imageLoadFailureByUrl,
            imageLoadingUrls = content.imageLoadingUrls,
            searchInputValue = session.searchInputValue,
            isSearchMode = session.isSearchMode,
            sortType = session.sortType,
            treeOrder = content.treeOrder,
            treeDepthMap = content.treeDepthMap,
            treeRootMap = content.treeRootMap,
            postGroups = content.postGroups,
            lastLoadedResCount = content.lastLoadedResCount,
            latestArrivalGroupIndex = content.latestArrivalGroupIndex,
            firstNewResNo = content.firstNewResNo ?: tab.firstNewResNo,
            prevResCount = if (content.prevResCount != 0) content.prevResCount else tab.prevResCount,
            isAutoScroll = session.isAutoScroll,
            showMinimapScrollbar = settings.showMinimapScrollbar,
            pendingToastResId = session.pendingToastResId,
            textScale = settings.textScale,
            isIndividualTextScale = settings.isIndividualTextScale,
            headerTextScale = settings.headerTextScale,
            bodyTextScale = settings.bodyTextScale,
            lineHeight = settings.lineHeight,
            visiblePostRows = visibleRowsResult?.visiblePostRows ?: emptyList(),
            replyCounts = visibleRowsResult?.replyCounts ?: emptyList(),
            firstAfterIndex = visibleRowsResult?.firstAfterIndex ?: -1,
            gestureSettings = settings.gestureSettings,
            isTabSwipeEnabled = shouldEnableTabSwipe(session.copy(popupStack = popupStack)),
        )
    }

    /** タブ key に対応する SessionState を更新する。 */
    private fun updateThreadSessionState(
        tabKey: String,
        transform: (ThreadSessionState) -> ThreadSessionState,
    ) {
        tabSessionStore.updateThreadSessionState(ThreadId(tabKey), transform)
    }

    /** タブ key に対応する content state を更新する。 */
    private fun updateContentState(
        tabKey: String,
        transform: (ThreadRouteContentState) -> ThreadRouteContentState,
    ) {
        contentStates.update { states ->
            states + (tabKey to transform(states[tabKey] ?: ThreadRouteContentState()))
        }
    }

    /** ポップアップを重複抑止つきで追加する。 */
    private fun appendPopup(tabKey: String, info: PopupInfo) {
        updateThreadSessionState(tabKey) { state ->
            val updatedStack = appendPopupIfDistinct(state.popupStack, info)
            val next = state.copy(popupStack = updatedStack)
            next.copy(isTabSwipeEnabled = shouldEnableTabSwipe(next))
        }
    }

    /** ポップアップ用の安定 ID を採番する。 */
    private fun nextPopupId(tabKey: String): Long {
        val threadId = ThreadId(tabKey)
        var nextId = 1L
        tabSessionStore.updateThreadRuntimeState(threadId) { state ->
            nextId = state.nextPopupId
            state.copy(nextPopupId = state.nextPopupId + 1)
        }
        return nextId
    }

    /** セッション状態からタブ横スワイプ可否を判定する。 */
    private fun shouldEnableTabSwipe(state: ThreadSessionState): Boolean {
        return !state.isSearchMode && state.popupStack.isEmpty()
    }

    /** 画像URL一覧を正規化する。 */
    private fun buildImageMenuUrls(primaryUrl: String, imageUrls: List<String>): List<String> {
        val normalized = distinctImageUrls(imageUrls).filter { it.isNotBlank() }.toMutableList()
        if (primaryUrl.isNotBlank() && primaryUrl !in normalized) {
            normalized.add(0, primaryUrl)
        }
        return normalized
    }

    /** 表示対象の画像URL集合を抽出する。 */
    private fun deriveActiveImageUrls(posts: List<ThreadPostUiModel>): Set<String> {
        return posts.asSequence()
            .flatMap { post -> extractImageUrls(post.body.content).asSequence() }
            .filter { url -> url.isNotBlank() }
            .toSet()
    }

    /** NG定義を boardId + regex 形式に変換する。 */
    private fun compileNgs(ngList: List<NgEntity>): List<Triple<Long?, Regex, com.websarva.wings.android.slevo.data.model.NgType>> {
        return ngList.mapNotNull { ng ->
            runCatching {
                val regex = if (ng.isRegex) Regex(ng.pattern) else Regex(Regex.escape(ng.pattern))
                Triple(ng.boardId, regex, ng.type)
            }.getOrNull()
        }
    }

    /** 投稿一覧に NG を適用して非表示番号集合を返す。 */
    private fun deriveNgPostNumbers(
        posts: List<ThreadPostUiModel>,
        boardId: Long,
        compiledNg: List<Triple<Long?, Regex, com.websarva.wings.android.slevo.data.model.NgType>>,
    ): Set<Int> {
        return posts.mapIndexedNotNull { index, post ->
            val isNg = compiledNg.any { (targetBoardId, regex, type) ->
                (targetBoardId == null || targetBoardId == boardId) && runCatching {
                    val target = when (type) {
                        com.websarva.wings.android.slevo.data.model.NgType.USER_ID -> post.header.id
                        com.websarva.wings.android.slevo.data.model.NgType.USER_NAME -> post.header.name
                        com.websarva.wings.android.slevo.data.model.NgType.WORD -> post.body.content
                        else -> ""
                    }
                    regex.containsMatchIn(target)
                }.getOrDefault(false)
            }
            if (isNg) index + 1 else null
        }.toSet()
    }

    /** 新着グループを差分更新する。 */
    private fun updatePostGroups(
        previousGroups: List<ThreadPostGroup>,
        previousResCount: Int,
        posts: List<ThreadPostUiModel>,
        initialUnreadStartResNo: Int?,
        isInitialLoad: Boolean,
    ): ThreadRoutePostGroupState = updateThreadPostGroups(
        previousGroups = previousGroups,
        previousResCount = previousResCount,
        posts = posts,
        initialUnreadStartResNo = initialUnreadStartResNo,
        isInitialLoad = isInitialLoad,
    )

    /** タイトル未取得時の初期表示名を組み立てる。 */
    private fun buildInitialThreadTitle(
        boardUrl: String,
        threadKey: String,
        threadTitle: String?
    ): String {
        threadTitle?.takeIf { it.isNotBlank() }?.let { return it }
        val parsed = com.websarva.wings.android.slevo.ui.util.parseBoardUrl(boardUrl) ?: return ""
        val (host, boardKey) = parsed
        return "https://$host/test/read.cgi/$boardKey/$threadKey/"
    }

    /** 表示不能になったポップアップを除外する。 */
    private fun prunePopupStackWithoutRenderablePosts(
        popupStack: List<PopupInfo>,
        posts: List<ThreadPostUiModel>,
        ngPostNumbers: Set<Int>,
    ): List<PopupInfo> {
        if (popupStack.isEmpty()) {
            return popupStack
        }
        return popupStack.filter { info ->
            info.postNumbers.any { number -> number in 1..posts.size && number !in ngPostNumbers }
        }
    }

    /** 閉じたタブのキャッシュと監視ジョブを解放する。 */
    private fun evictClosedTabs(openKeys: Set<String>) {
        val trackedKeys = buildSet {
            addAll(uiStateCache.keys)
            addAll(contentStates.value.keys)
            addAll(initializationJobs.keys)
            addAll(threadLoadJobs.keys)
            addAll(postSuccessCollectJobs.keys)
            addAll(myPostCollectJobs.keys)
        }
        trackedKeys.filterNot(openKeys::contains).forEach { key ->
            uiStateCache.remove(key)
            initializationJobs.remove(key)?.cancel()
            threadLoadJobs.remove(key)?.cancel()
            postSuccessCollectJobs.remove(key)?.cancel()
            myPostCollectJobs.remove(key)?.cancel()
        }
        contentStates.update { states -> states.filterKeys { it in openKeys } }
    }

    /** 指定 key のスレッドタブが現在も開いているかを返す。 */
    private fun isThreadTabOpen(tabKey: String): Boolean {
        return tabSessionStore.openThreadTabs.value.any { it.id.value == tabKey }
    }
}

/**
 * `ThreadUiState` 合成に影響する `ThreadTabInfo` の比較キー。
 *
 * スクロール位置の保存では再合成を発火させないため、scroll offset 系は含めない。
 */
private data class ThreadTabUiStateSourceKey(
    val id: ThreadId,
    val title: String,
    val boardName: String,
    val boardUrl: String,
    val boardId: Long,
    val resCount: Int,
    val newResCount: Int,
    val prevResCount: Int,
    val lastReadResNo: Int,
    val firstNewResNo: Int?,
    val bookmarkColorName: String?,
    val isPinned: Boolean,
)

/** `ThreadTabInfo` から `ThreadUiState` 合成用の比較キーを作る。 */
private fun ThreadTabInfo.toUiStateSourceKey(): ThreadTabUiStateSourceKey {
    return ThreadTabUiStateSourceKey(
        id = id,
        title = title,
        boardName = boardName,
        boardUrl = boardUrl,
        boardId = boardId,
        resCount = resCount,
        newResCount = newResCount,
        prevResCount = prevResCount,
        lastReadResNo = lastReadResNo,
        firstNewResNo = firstNewResNo,
        bookmarkColorName = bookmarkColorName,
        isPinned = isPinned,
    )
}

/**
 * route-level で保持するスレッド本文と派生状態。
 */
private data class ThreadRouteContentState(
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val threadInfo: ThreadInfo = ThreadInfo(),
    val posts: List<ThreadPostUiModel>? = null,
    val idCountMap: Map<String, Int> = emptyMap(),
    val idIndexList: List<Int> = emptyList(),
    val replySourceMap: Map<Int, List<Int>> = emptyMap(),
    val treeOrder: List<Int> = emptyList(),
    val treeDepthMap: Map<Int, Int> = emptyMap(),
    val treeRootMap: Map<Int, Int> = emptyMap(),
    val postGroups: List<ThreadPostGroup> = emptyList(),
    val lastLoadedResCount: Int = 0,
    val latestArrivalGroupIndex: Int? = null,
    /** 初回ロード時に固定する未読グループの開始レス番号。 */
    val initialUnreadStartResNo: Int? = null,
    val initialUnreadBoundaryInitialized: Boolean = false,
    val firstNewResNo: Int? = null,
    val prevResCount: Int = 0,
    val myPostNumbers: Set<Int> = emptySet(),
    val imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    val imageLoadingUrls: Set<String> = emptySet(),
    val observedThreadHistoryId: Long? = null,
)

/**
 * route-level でまとめて購読する設定値。
 */
private data class ThreadRouteSettingsState(
    val textScale: Float,
    val isIndividualTextScale: Boolean,
    val headerTextScale: Float,
    val bodyTextScale: Float,
    val lineHeight: Float,
    val showMinimapScrollbar: Boolean,
    val gestureSettings: com.websarva.wings.android.slevo.data.model.GestureSettings,
)

/**
 * テキスト表示設定だけをまとめた中間状態。
 */
private data class ThreadRouteTextSettingsState(
    val textScale: Float,
    val isIndividualTextScale: Boolean,
    val headerTextScale: Float,
    val bodyTextScale: Float,
    val lineHeight: Float,
)

/**
 * `ThreadUiState` 合成前の結合済み入力。
 */
private data class ThreadRouteBaseUiStateInput(
    val tab: ThreadTabInfo?,
    val session: ThreadSessionState,
    val content: ThreadRouteContentState,
    val settings: ThreadRouteSettingsState,
    val bookmarkStatus: BookmarkStatusState,
)

/**
 * 投稿グループ更新結果。
 */
internal data class ThreadRoutePostGroupState(
    val groups: List<ThreadPostGroup>,
    val lastLoadedResCount: Int,
    val latestArrivalGroupIndex: Int?,
)

/** レスポンスの取得結果から、更新単位のグループと最新グループ位置を構築する。 */
internal fun updateThreadPostGroups(
    previousGroups: List<ThreadPostGroup>,
    previousResCount: Int,
    posts: List<ThreadPostUiModel>,
    initialUnreadStartResNo: Int?,
    isInitialLoad: Boolean,
): ThreadRoutePostGroupState {
    val newResCount = posts.size
    // --- Empty response ---
    if (newResCount == 0) {
        return ThreadRoutePostGroupState(emptyList(), newResCount, null)
    }

    // --- Initial load ---
    if (isInitialLoad) {
        return buildInitialPostGroupState(newResCount, initialUnreadStartResNo)
    }

    // --- Recovery after a completed empty state ---
    if (previousResCount == 0 || previousGroups.isEmpty()) {
        val recoveryGroups = listOf(
            ThreadPostGroup(startResNo = 1, endResNo = newResCount, prevResCount = 0)
        )
        return ThreadRoutePostGroupState(recoveryGroups, newResCount, null)
    }

    // --- Response count decrease ---
    if (newResCount < previousResCount) {
        // Fallback: a shortened response list starts a fresh non-arrival group.
        val resetGroups = listOf(
            ThreadPostGroup(startResNo = 1, endResNo = newResCount, prevResCount = 0)
        )
        return ThreadRoutePostGroupState(resetGroups, newResCount, null)
    }
    // --- Response append ---
    if (newResCount > previousResCount) {
        val nextGroups = previousGroups + ThreadPostGroup(
            startResNo = previousResCount + 1,
            endResNo = newResCount,
            prevResCount = previousResCount,
        )
        return ThreadRoutePostGroupState(nextGroups, newResCount, nextGroups.lastIndex)
    }
    // --- No change ---
    return ThreadRoutePostGroupState(previousGroups, newResCount, null)
}

/** 初回ロードのレス範囲を既読グループと未読グループへ分割する。 */
private fun buildInitialPostGroupState(
    newResCount: Int,
    initialUnreadStartResNo: Int?,
): ThreadRoutePostGroupState {
    // --- Boundary validation ---
    val validUnreadStart = initialUnreadStartResNo?.takeIf { it in 1..newResCount }
    if (validUnreadStart == null) {
        val initialGroups = listOf(
            ThreadPostGroup(startResNo = 1, endResNo = newResCount, prevResCount = 0)
        )
        return ThreadRoutePostGroupState(initialGroups, newResCount, null)
    }

    // --- Unread from first response ---
    if (validUnreadStart == 1) {
        val unreadGroups = listOf(
            ThreadPostGroup(startResNo = 1, endResNo = newResCount, prevResCount = 0)
        )
        return ThreadRoutePostGroupState(unreadGroups, newResCount, 0)
    }

    // --- Read-unread split ---
    val initialGroups = listOf(
        ThreadPostGroup(
            startResNo = 1,
            endResNo = validUnreadStart - 1,
            prevResCount = 0,
        ),
        ThreadPostGroup(
            startResNo = validUnreadStart,
            endResNo = newResCount,
            prevResCount = validUnreadStart - 1,
        ),
    )
    return ThreadRoutePostGroupState(initialGroups, newResCount, 1)
}
