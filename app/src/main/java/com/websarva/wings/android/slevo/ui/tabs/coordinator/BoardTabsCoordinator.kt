package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ボードタブの状態を管理するコーディネーター。
 *
 * - 役割: 開いているボードタブ一覧の状態管理（追加/削除/スクロール位置）および
 *   ローカルリポジトリへの保存・読み込みを仲介する。
 * - スコープ: Activity retained スコープに準拠し、構成変更を超えてインスタンスが生存する。
 * - 主な公開プロパティ:
 *   - `openBoardTabs`: 現在開かれているボードタブの一覧（StateFlow）。
 *   - `boardLoaded`: リポジトリからの初期読み込みが完了したかどうかのフラグ。
 *   - `selectedBoardTabKey`: 現在選択中の板タブ key。正規化済み boardUrl を保持する。
 *
 * 実装ノート:
 * - `bind` で `tabsRepository` と `bookmarkBoardRepository` を combine してタブ情報を構築する。
 * - `upsertBoardTab` は同一 boardUrl が存在すれば上書き、なければ末尾に追加する。
 * - タブ削除時は selected key を隣接タブまたは先頭タブへ補正する。
 */
@ActivityRetainedScoped
class BoardTabsCoordinator @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val bookmarkBoardRepository: BookmarkBoardRepository,
) {
    // 現在開かれているボードタブの一覧。UI はこれを監視してタブ表示を行う。
    private val _openBoardTabs = MutableStateFlow<List<BoardTabInfo>>(emptyList())
    val openBoardTabs: StateFlow<List<BoardTabInfo>> = _openBoardTabs.asStateFlow()

    // 初回のリポジトリ読み込みが完了したかどうか。
    private val _boardLoaded = MutableStateFlow(false)
    val boardLoaded: StateFlow<Boolean> = _boardLoaded.asStateFlow()

    // 現在選択中の板タブ key。正規化済み boardUrl を保持する。
    private val _selectedBoardTabKey = MutableStateFlow<String?>(null)
    val selectedBoardTabKey: StateFlow<String?> = _selectedBoardTabKey.asStateFlow()

    private val _boardSessionStates = MutableStateFlow<Map<String, BoardSessionState>>(emptyMap())
    val boardSessionStates: StateFlow<Map<String, BoardSessionState>> = _boardSessionStates.asStateFlow()

    private val _boardCurrentPage = MutableStateFlow(-1)

    // ページ遷移用のアニメーションイベント。オフセットではなくターゲットインデックスを送る。
    private val _boardPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val boardPageAnimation: SharedFlow<Int> = _boardPageAnimation.asSharedFlow()

    private var scope: CoroutineScope? = null

    /**
     * Coordinator をライフサイクルに結びつける。bind は一度だけ呼ばれる想定。
     * - scope: UI の CoroutineScope（例: ViewModelScope / LifecycleScope）
     *
     * 内部では `tabsRepository.observeOpenBoardTabs()` と `bookmarkBoardRepository.observeGroupsWithBoards()` を
     * combine して、ブックマークの色情報を各タブに合成する。取得したタブ一覧は `_openBoardTabs` に反映される。
     */
    fun bind(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        scope.launch {
            combine(
                tabsRepository.observeOpenBoardTabs(),
                bookmarkBoardRepository.observeGroupsWithBoards()
            ) { tabs, groups ->
                // groups を走査して boardId -> colorName のマップを作成し、
                // tabs に対して bookmarkColorName を埋める。
                val colorMap = mutableMapOf<Long, String>()
                groups.forEach { g ->
                    val color = g.group.colorName
                    g.boards.forEach { b -> colorMap[b.boardId] = color }
                }
                // 各タブに対して colorMap から bookmarkColorName を付与する。
                tabs.map { tab -> tab.copy(bookmarkColorName = colorMap[tab.boardId]) }
            }.collect { boards ->
                _openBoardTabs.value = boards
                syncBoardCurrentPageFromSelectedKey(boards)
                _boardLoaded.value = true
            }
        }
    }

    /**
     * 指定された `AppRoute.Board` に対応するタブを保証する（存在しなければ追加）。
     * 戻り値はタブのインデックス。
     * - 呼び出し後、タブ一覧はリポジトリに保存される。
     */
    fun ensureBoardTab(route: AppRoute.Board): Int {
        val index = upsertBoardTab(
            BoardTabInfo(
                boardId = route.boardId ?: 0L,
                boardName = route.boardName,
                boardUrl = route.boardUrl,
                serviceName = parseServiceName(route.boardUrl)
            )
        )
        saveBoardTabs()
        return index
    }

    /**
     * 渡された `BoardTabInfo` を開く（既存があれば更新、なければ追加）し、保存する。
     */
    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        upsertBoardTab(boardTabInfo)
        saveBoardTabs()
    }

    /**
     * 選択中の板タブ key を更新する。
     */
    fun selectBoardTab(boardUrl: String?) {
        _selectedBoardTabKey.value = boardUrl?.takeIf { target ->
            _openBoardTabs.value.any { it.boardUrl == target }
        }
        syncBoardCurrentPageFromSelectedKey()
    }

    /**
     * 指定したタブを閉じる。
     *
     * セッション状態と選択 key を整理し、現在ページが削除により変化する場合は
     * `updateCurrentPageAfterRemoval` で補正を行う。
     */
    fun closeBoardTab(tab: BoardTabInfo) {
        val selectedKeyBeforeRemoval = _selectedBoardTabKey.value
        val removedTabKey = tab.boardUrl
        val removedIndex = _openBoardTabs.value.indexOfFirst { it.boardUrl == tab.boardUrl }
        var updatedTabs: List<BoardTabInfo> = emptyList()
        _openBoardTabs.update { state ->
            val newTabs = state.filterNot { it.boardUrl == tab.boardUrl }
            updatedTabs = newTabs
            newTabs
        }
        _boardSessionStates.update { it - tab.boardUrl }
        updateSelectedBoardKeyAfterRemoval(selectedKeyBeforeRemoval, removedTabKey, removedIndex, updatedTabs)
        saveBoardTabs(updatedTabs)
    }

    /**
     * boardUrl から該当タブを探して閉じるユーティリティ。
     */
    fun closeBoardTabByUrl(boardUrl: String) {
        _openBoardTabs.value.find { it.boardUrl == boardUrl }?.let { tab ->
            closeBoardTab(tab)
        }
    }

    /**
     * 指定した boardUrl の板タブの固定状態を切り替えて保存する。
     */
    fun togglePinBoardTab(boardUrl: String) {
        _openBoardTabs.update { state ->
            state.map { tab ->
                if (tab.boardUrl == boardUrl) {
                    tab.copy(isPinned = !tab.isPinned)
                } else {
                    tab
                }
            }
        }
        saveBoardTabs()
    }

    /**
     * 指定タブのスクロール位置（firstVisibleIndex とオフセット）を更新して保存する。
     * - UI のスクロールイベントから呼ばれる想定。
     */
    fun updateBoardScrollPosition(
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int,
    ) {
        _openBoardTabs.update { state ->
            state.map { tab ->
                if (tab.boardUrl == boardUrl) {
                    tab.copy(
                        firstVisibleItemIndex = firstVisibleIndex,
                        firstVisibleItemScrollOffset = scrollOffset,
                    )
                } else {
                    tab
                }
            }
        }
        saveBoardTabs()
    }

    /**
     * 指定板タブの揮発 UI セッション状態を返す。
     */
    fun getBoardSessionState(boardUrl: String): BoardSessionState {
        return _boardSessionStates.value[boardUrl] ?: BoardSessionState()
    }

    /**
     * 指定板タブの揮発 UI セッション状態を更新する。
     */
    fun updateBoardSessionState(
        boardUrl: String,
        transform: (BoardSessionState) -> BoardSessionState,
    ) {
        _boardSessionStates.update { states ->
            val current = states[boardUrl] ?: BoardSessionState()
            states + (boardUrl to transform(current))
        }
    }

    /**
     * ensure 済みの boardId を既存板タブへ反映して永続状態も更新する。
     *
     * URL から開いた placeholder boardId のタブを、Repository で解決した実 boardId に差し替える。
     */
    fun updateBoardResolvedInfo(
        boardUrl: String,
        boardId: Long,
        boardName: String? = null,
    ) {
        if (boardId == 0L) return
        _openBoardTabs.update { tabs ->
            tabs.map { tab ->
                if (tab.boardUrl == boardUrl) {
                    tab.copy(
                        boardId = boardId,
                        boardName = boardName?.takeIf(String::isNotBlank) ?: tab.boardName,
                    )
                } else {
                    tab
                }
            }
        }
        saveBoardTabs()
    }

    /**
     * アニメーション付きでページ移動を通知する。内部で SharedFlow にターゲットインデックスを emit する。
     */
    fun animateBoardPage(offset: Int) {
        val tabs = _openBoardTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _boardCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            scope?.launch { _boardPageAnimation.emit(targetIndex) }
        }
    }

    /**
     * boardTabInfo を upsert（更新または追加）する内部ユーティリティ。
     * - 既存の boardUrl と一致するタブがあればその位置を保持して必要なフィールドを更新する。
     *   ただしスクロール位置（firstVisibleItemIndex / firstVisibleItemScrollOffset）は既存のものを保持する。
     * - 新規追加の場合は末尾に追加する。
     * - 戻り値は対象のインデックス（既存ならその index、追加なら追加後の index）
     */
    private fun upsertBoardTab(boardTabInfo: BoardTabInfo): Int {
        var targetIndex = -1
        _openBoardTabs.update { state ->
            val currentBoards = state
            val index = currentBoards.indexOfFirst { it.boardUrl == boardTabInfo.boardUrl }
            val updated = if (index != -1) {
                targetIndex = index
                currentBoards.toMutableList().apply {
                    val existing = this[index]
                    // 既存タブは固定状態とスクロール位置を保持しつつ、他の情報（名前やブックマーク色）を更新する
                    this[index] = boardTabInfo.copy(
                        bookmarkColorName = boardTabInfo.bookmarkColorName ?: existing.bookmarkColorName,
                        firstVisibleItemIndex = existing.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = existing.firstVisibleItemScrollOffset,
                        isPinned = existing.isPinned,
                    )
                }
            } else {
                // 新規追加は末尾に追加
                targetIndex = currentBoards.size
                currentBoards + boardTabInfo
            }
            updated
        }
        return targetIndex
    }

    /**
     * 現在のタブ一覧をリポジトリに保存する。scope がバインドされている場合のみ非同期で保存を実行する。
     */
    private fun saveBoardTabs(tabs: List<BoardTabInfo> = _openBoardTabs.value) {
        scope?.launch { tabsRepository.saveOpenBoardTabs(tabs) }
    }

    /**
     * selected key から互換用 currentPage を導出する。
     */
    private fun syncBoardCurrentPageFromSelectedKey(tabs: List<BoardTabInfo> = _openBoardTabs.value) {
        val selectedKey = _selectedBoardTabKey.value
        _boardCurrentPage.value = when {
            tabs.isEmpty() -> -1
            selectedKey == null -> -1
            else -> tabs.indexOfFirst { it.boardUrl == selectedKey }
        }
    }

    /**
     * タブ削除後に selected key を補正する。
     */
    private fun updateSelectedBoardKeyAfterRemoval(
        selectedKeyBeforeRemoval: String?,
        removedTabKey: String,
        removedIndex: Int,
        updatedTabs: List<BoardTabInfo>,
    ) {
        val removedTabWasSelected = removedIndex >= 0 && selectedKeyBeforeRemoval == removedTabKey

        _selectedBoardTabKey.value = when {
            updatedTabs.isEmpty() -> null
            !removedTabWasSelected && selectedKeyBeforeRemoval != null && updatedTabs.any { it.boardUrl == selectedKeyBeforeRemoval } -> selectedKeyBeforeRemoval
            removedIndex in updatedTabs.indices -> updatedTabs[removedIndex].boardUrl
            else -> updatedTabs.last().boardUrl
        }
        syncBoardCurrentPageFromSelectedKey(updatedTabs)
    }
}
