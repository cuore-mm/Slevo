package com.websarva.wings.android.bbsviewer.ui.tabs

data class BoardTabInfo(
    val boardId: Long,
    val boardName: String,
    val boardUrl: String,
    val serviceName: String,
    val firstVisibleItemIndex: Int = 0, // スクロール位置（インデックス）
    val firstVisibleItemScrollOffset: Int = 0 // スクロール位置（オフセット）
)
