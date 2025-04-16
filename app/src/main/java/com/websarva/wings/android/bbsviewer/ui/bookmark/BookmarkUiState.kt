package com.websarva.wings.android.bbsviewer.ui.bookmark

import com.websarva.wings.android.bbsviewer.data.local.entity.BookmarkThreadEntity

data class BookmarkUiState(
    val bookmarks: List<BookmarkThreadEntity>? = null,
    val isLoading: Boolean = false
)
