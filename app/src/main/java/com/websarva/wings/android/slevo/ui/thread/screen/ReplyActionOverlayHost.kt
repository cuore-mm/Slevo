package com.websarva.wings.android.slevo.ui.thread.screen

import androidx.compose.runtime.Composable
import com.websarva.wings.android.slevo.ui.thread.res.PostDialogTarget
import com.websarva.wings.android.slevo.ui.thread.res.PostItemDialogs
import com.websarva.wings.android.slevo.ui.thread.res.PostItemDialogState
import com.websarva.wings.android.slevo.ui.thread.sheet.ReplyActionMenuSheet
import kotlinx.coroutines.CoroutineScope

/**
 * レスの操作メニューとダイアログを統合して表示するホスト。
 *
 * 直前のメニュー対象を保持し、選択アクションに応じてダイアログ表示や返信処理を委譲する。
 */
@Composable
fun ReplyActionOverlayHost(
    menuTarget: PostDialogTarget?,
    dialogTarget: PostDialogTarget?,
    boardName: String,
    boardId: Long,
    scope: CoroutineScope,
    dialogState: PostItemDialogState,
    onClearMenuTarget: () -> Unit,
    onReply: (PostDialogTarget) -> Unit,
    onCopy: (PostDialogTarget) -> Unit,
    onNg: (PostDialogTarget) -> Unit,
) {
    menuTarget?.let { target ->
        ReplyActionMenuSheet(
            postNum = target.postNum,
            onReplyClick = {
                onClearMenuTarget()
                onReply(target)
            },
            onCopyClick = {
                onClearMenuTarget()
                onCopy(target)
            },
            onNgClick = {
                onClearMenuTarget()
                onNg(target)
            },
            onDismiss = onClearMenuTarget,
        )
    }

    PostItemDialogs(
        target = dialogTarget,
        boardName = boardName,
        boardId = boardId,
        scope = scope,
        dialogState = dialogState,
    )
}
