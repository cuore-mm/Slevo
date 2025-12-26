package com.websarva.wings.android.slevo.ui.thread.res

import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel

/**
 * メニューやダイアログで操作対象となる投稿情報をまとめて保持する。
 *
 * 投稿番号と投稿データの対応を維持し、UIイベントの参照に使う。
 */
data class PostDialogTarget(
    val post: ThreadPostUiModel,
    val postNum: Int,
)
