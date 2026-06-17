package com.websarva.wings.android.slevo.ui.tabs.model

/**
 * タブ一覧と板画面間で受け渡す、開いている板タブの軽量表示モデル。
 *
 * タブ識別子、タイトル、固定状態、スクロール位置のような永続復元対象だけを保持し、
 * 検索やシート表示のような揮発 UI 状態は `BoardSessionState` で別管理する。
 */
data class BoardTabInfo(
    val boardId: Long,
    val boardName: String,
    val boardUrl: String,
    val serviceName: String,
    val firstVisibleItemIndex: Int = 0, // スクロール位置（インデックス）
    val firstVisibleItemScrollOffset: Int = 0, // スクロール位置（オフセット）
    val bookmarkColorName: String? = null,
    val isPinned: Boolean = false
)
