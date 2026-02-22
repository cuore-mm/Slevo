package com.websarva.wings.android.slevo.ui.bbsroute

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState

/**
 * 板/スレ画面で共有するUI状態のインターフェース。
 *
 * 共通プロパティの更新手段を定義する。
 */
interface BaseUiState<T> where T : BaseUiState<T> {
    val boardInfo: BoardInfo
    val bookmarkStatusState: BookmarkStatusState
    val bookmarkSheetState: BookmarkSheetUiState
    val loadProgress: Float
    val gestureSettings: GestureSettings
    val isLoading: Boolean
    val isTabSwipeEnabled: Boolean

    // 共通プロパティを更新して、自身の具象型の新しいインスタンスを返すメソッド
    fun copyState(
        boardInfo: BoardInfo = this.boardInfo,
        bookmarkStatusState: BookmarkStatusState = this.bookmarkStatusState,
        bookmarkSheetState: BookmarkSheetUiState = this.bookmarkSheetState,
        loadProgress: Float = this.loadProgress,
        gestureSettings: GestureSettings = this.gestureSettings,
        isLoading: Boolean = this.isLoading,
        isTabSwipeEnabled: Boolean = this.isTabSwipeEnabled,
    ): T
}
