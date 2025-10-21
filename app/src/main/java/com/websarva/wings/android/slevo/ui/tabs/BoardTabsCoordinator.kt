package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.hilt.android.scopes.ViewModelScoped
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

@ViewModelScoped
/**
 * ボードタブの状態を管理するコーディネーター。
 *
 * - 役割: 開いているボードタブ一覧の状態管理（追加/削除/スクロール位置）および
 *   ローカルリポジトリへの保存・読み込みを仲介する。
 * - スコープ: ViewModel スコープに準拠し、UI のライフサイクルに合わせてインスタンスが生存する。
 * - 主な公開プロパティ:
 *   - `openBoardTabs`: 現在開かれているボードタブの一覧（StateFlow）。
 *   - `boardLoaded`: リポジトリからの初期読み込みが完了したかどうかのフラグ。
 *   - `boardCurrentPage`: 現在表示中のタブインデックス。未選択は -1 を表す。
 *
 * 実装ノート:
 * - `bind` で `tabsRepository` と `bookmarkBoardRepository` を combine してタブ情報を構築する。
 * - `upsertBoardTab` は同一 boardUrl が存在すれば上書き、なければ末尾に追加する。
 * - タブ削除時は `updateCurrentPageAfterRemoval` で現在ページの補正を行う。
 */
class BoardTabsCoordinator @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val bookmarkBoardRepository: BookmarkBoardRepository,
    private val tabViewModelRegistry: TabViewModelRegistry,
) {
    // 現在開かれているボードタブの一覧。UI はこれを監視してタブ表示を行う。
    private val _openBoardTabs = MutableStateFlow<List<BoardTabInfo>>(emptyList())
    val openBoardTabs: StateFlow<List<BoardTabInfo>> = _openBoardTabs.asStateFlow()

    // 初回のリポジトリ読み込みが完了したかどうか。
    private val _boardLoaded = MutableStateFlow(false)
    val boardLoaded: StateFlow<Boolean> = _boardLoaded.asStateFlow()

    // 現在選択中のタブインデックス。0 以上が有効、未選択は -1。
    private val _boardCurrentPage = MutableStateFlow(-1)
    val boardCurrentPage: StateFlow<Int> = _boardCurrentPage.asStateFlow()

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
     * 指定したタブを閉じる（キャッシュしている ViewModel も解放する）。
     * - ViewModel は `tabViewModelRegistry.releaseBoardViewModel` で解放する。
     * - 現在ページが削除により変化する場合は `updateCurrentPageAfterRemoval` で補正を行う。
     */
    fun closeBoardTab(tab: BoardTabInfo) {
        // 関連する ViewModel を解放
        tabViewModelRegistry.releaseBoardViewModel(tab.boardUrl)

        val removedIndex = _openBoardTabs.value.indexOfFirst { it.boardUrl == tab.boardUrl }
        var updatedTabs: List<BoardTabInfo> = emptyList()
        _openBoardTabs.update { state ->
            val newTabs = state.filterNot { it.boardUrl == tab.boardUrl }
            updatedTabs = newTabs
            newTabs
        }
        updateCurrentPageAfterRemoval(_boardCurrentPage, removedIndex, updatedTabs.size)
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
     * 現在のページを直接セットする。
     */
    fun setBoardCurrentPage(page: Int) {
        _boardCurrentPage.value = page
    }

    /**
     * offset 分だけページを移動する（範囲外なら無視）。
     */
    fun moveBoardPage(offset: Int) {
        val tabs = _openBoardTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _boardCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            setBoardCurrentPage(targetIndex)
        }
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
                    // 既存タブはスクロール位置を保持しつつ、他の情報（名前やブックマーク色）を更新する
                    this[index] = boardTabInfo.copy(
                        bookmarkColorName = boardTabInfo.bookmarkColorName ?: existing.bookmarkColorName,
                        firstVisibleItemIndex = existing.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = existing.firstVisibleItemScrollOffset,
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
     * タブ削除後に現在ページ（index）を補正するロジック。
     * - currentPageFlow: 現在ページを保持する MutableStateFlow
     * - removedIndex: 削除されたタブのインデックス（存在しなければ -1）
     * - updatedSize: 削除後のタブ数
     *
     * ケース一覧（実装ロジックに基づく）:
     * - updatedSize <= 0 -> -1（タブ無し）
     * - current < 0 -> 変更なし（選択なしのまま）
     * - removedIndex == -1 -> 現在インデックスを bounds 内に収める
     * - current == removedIndex -> 削除位置を最大値に丸めた値にする（削除されたタブの右隣が選択済みならそちらに移る）
     * - current > removedIndex -> current - 1 にして bounds に収める（左に寄せる）
     * - current >= updatedSize -> updatedSize - 1 にする
     */
    private fun updateCurrentPageAfterRemoval(
        currentPageFlow: MutableStateFlow<Int>,
        removedIndex: Int,
        updatedSize: Int,
    ) {
        val current = currentPageFlow.value
        val newPage = when {
            updatedSize <= 0 -> -1
            current < 0 -> current
            removedIndex == -1 -> current.coerceIn(0, updatedSize - 1)
            current == removedIndex -> removedIndex.coerceAtMost(updatedSize - 1)
            current > removedIndex -> (current - 1).coerceIn(0, updatedSize - 1)
            current >= updatedSize -> updatedSize - 1
            else -> current
        }
        currentPageFlow.value = newPage
    }
}
