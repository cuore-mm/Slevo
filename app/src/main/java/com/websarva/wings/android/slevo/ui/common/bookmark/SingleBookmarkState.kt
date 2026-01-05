package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.Groupable

/**
 * ブックマークシートとグループ編集ダイアログの表示状態をまとめた UI 状態。
 *
 * 板/スレッド共通のブックマーク操作で利用する。
 */
data class SingleBookmarkState(
    val isBookmarked: Boolean = false,
    val groups: List<Groupable> = emptyList(),
    val selectedGroup: Groupable? = null,
    val showBookmarkSheet: Boolean = false,
    val groupDialogState: GroupDialogState = GroupDialogState(),
)
