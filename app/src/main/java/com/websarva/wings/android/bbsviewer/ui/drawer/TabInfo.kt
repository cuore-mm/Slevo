package com.websarva.wings.android.bbsviewer.ui.drawer

data class TabInfo(
    val key: String,
    val title: String,
    val boardName: String,
    val boardUrl: String,
    val boardId: Long,
    val firstVisibleItemIndex: Int = 0, // スクロール位置（インデックス）
    val firstVisibleItemScrollOffset: Int = 0 // スクロール位置（オフセット）
)
