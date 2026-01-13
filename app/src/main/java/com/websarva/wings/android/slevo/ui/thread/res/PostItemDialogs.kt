package com.websarva.wings.android.slevo.ui.thread.res

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.CopyDialog
import com.websarva.wings.android.slevo.ui.common.CopyItem
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.thread.dialog.NgSelectDialog
import com.websarva.wings.android.slevo.ui.thread.sheet.TextMenuSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 投稿関連ダイアログの表示状態を保持する。
 *
 * コピー/NG選択/テキストメニュー/NG登録の表示制御をまとめて扱う。
 */
@Stable
class PostItemDialogState internal constructor() {
    var copyDialogVisible by mutableStateOf(false)
        private set
    var ngSelectDialogVisible by mutableStateOf(false)
        private set
    var textMenuData by mutableStateOf<Pair<String, NgType>?>(null)
        private set
    var ngDialogData by mutableStateOf<Pair<String, NgType>?>(null)
        private set

    fun showCopyDialog() {
        copyDialogVisible = true
    }

    fun hideCopyDialog() {
        copyDialogVisible = false
    }

    fun showNgSelectDialog() {
        ngSelectDialogVisible = true
    }

    fun hideNgSelectDialog() {
        ngSelectDialogVisible = false
    }

    fun showTextMenu(text: String, type: NgType) {
        textMenuData = text to type
    }

    fun hideTextMenu() {
        textMenuData = null
    }

    fun openNgDialog(text: String, type: NgType) {
        ngDialogData = text to type
    }

    fun hideNgDialog() {
        ngDialogData = null
    }
}

/**
 * 投稿関連ダイアログの状態を保持する。
 */
@Composable
fun rememberPostItemDialogState(): PostItemDialogState {
    return remember { PostItemDialogState() }
}

/**
 * 投稿に紐づくダイアログやメニューの表示をまとめて制御する。
 *
 * 対象投稿がない場合はコピー/NG選択ダイアログを表示しない。
 */
@Composable
fun PostItemDialogs(
    target: PostDialogTarget?,
    boardName: String,
    boardId: Long,
    scope: CoroutineScope,
    dialogState: PostItemDialogState
) {
    val post = target?.post
    val postNum = target?.postNum

    // --- コピー ---
    if (dialogState.copyDialogVisible && post != null && postNum != null) {
        val header = buildString {
            append(postNum)
            if (post.header.name.isNotBlank()) append(" ${post.header.name}")
            if (post.header.date.isNotBlank()) append(" ${post.header.date}")
            if (post.header.id.isNotBlank()) append(" ID:${post.header.id}")
        }
        CopyDialog(
            items = listOf(
                CopyItem(postNum.toString(), stringResource(R.string.res_number_label)),
                CopyItem(post.header.name, stringResource(R.string.name_label)),
                CopyItem(post.header.id, stringResource(R.string.id_label)),
                CopyItem(post.body.content, stringResource(R.string.post_message)),
                CopyItem("$header\n${post.body.content}", stringResource(R.string.header_and_body)),
            ),
            onDismissRequest = { dialogState.hideCopyDialog() }
        )
    }

    // --- NG選択 ---
    if (dialogState.ngSelectDialogVisible && post != null) {
        NgSelectDialog(
            onNgIdClick = {
                dialogState.hideNgSelectDialog()
                dialogState.openNgDialog(post.header.id, NgType.USER_ID)
            },
            onNgNameClick = {
                dialogState.hideNgSelectDialog()
                dialogState.openNgDialog(post.header.name, NgType.USER_NAME)
            },
            onNgWordClick = {
                dialogState.hideNgSelectDialog()
                dialogState.openNgDialog(post.body.content, NgType.WORD)
            },
            onDismiss = { dialogState.hideNgSelectDialog() }
        )
    }

    // --- テキストメニュー ---
    dialogState.textMenuData?.let { (text, type) ->
        val clipboard = LocalClipboard.current
        TextMenuSheet(
            text = text,
            onCopyClick = {
                scope.launch {
                    val clip = ClipData.newPlainText("", text).toClipEntry()
                    clipboard.setClipEntry(clip)
                }
                dialogState.hideTextMenu()
            },
            onNgClick = {
                dialogState.hideTextMenu()
                dialogState.openNgDialog(text, type)
            },
            onDismiss = { dialogState.hideTextMenu() }
        )
    }

    // --- NGダイアログ ---
    dialogState.ngDialogData?.let { (text, type) ->
        NgDialogRoute(
            text = text,
            type = type,
            boardName = boardName,
            boardId = boardId.takeIf { it != 0L },
            onDismiss = { dialogState.hideNgDialog() }
        )
    }
}
