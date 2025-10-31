package com.websarva.wings.android.slevo.ui.bbsroute

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState

interface BaseUiState<T> where T : BaseUiState<T> {
    val boardInfo: BoardInfo
    val singleBookmarkState: SingleBookmarkState
    val loadProgress: Float
    val gestureSettings: GestureSettings
    val isLoading: Boolean

    // 共通プロパティを更新して、自身の具象型の新しいインスタンスを返すメソッド
    fun copyState(
        boardInfo: BoardInfo = this.boardInfo,
        singleBookmarkState: SingleBookmarkState = this.singleBookmarkState,
        loadProgress: Float = this.loadProgress,
        gestureSettings: GestureSettings = this.gestureSettings,
        isLoading: Boolean = this.isLoading,
    ): T
}
