package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo

/**
 * ブックマークシートの操作対象を表す共通インターフェース。
 *
 * 板/スレの混在は許可せず、同一型のtargetのみを扱う。
 */
sealed interface BookmarkTarget {
    val currentGroupId: Long?
}

/**
 * 板ブックマークの操作対象。
 *
 * 画面側で取得したBoardInfoと現在のグループIDを保持する。
 */
data class BoardTarget(
    val boardInfo: BoardInfo,
    override val currentGroupId: Long?
) : BookmarkTarget

/**
 * スレッドブックマークの操作対象。
 *
 * 画面側で取得したBoardInfoとThreadInfo、現在のグループIDを保持する。
 */
data class ThreadTarget(
    val boardInfo: BoardInfo,
    val threadInfo: ThreadInfo,
    override val currentGroupId: Long?
) : BookmarkTarget
