package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.Groupable

/**
 * ツールバー表示用のブックマーク状態を表すデータ。
 *
 * シートとは独立して現在のブックマーク有無とグループ情報を保持する。
 */
data class BookmarkStatusState(
    val isBookmarked: Boolean = false,
    val selectedGroup: Groupable? = null,
)
