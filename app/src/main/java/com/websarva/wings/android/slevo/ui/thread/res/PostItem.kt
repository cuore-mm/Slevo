package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.ui.thread.sheet.PostMenuSheet
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.navigation.AppRoute

/**
 * スレッドの投稿1件をヘッダー・本文・メディア・メニュー/ダイアログ込みで表示する。
 *
 * @param modifier 余白やサイズ調整のための修飾子。
 * @param post 表示対象の投稿データ。
 * @param postNum 投稿番号。
 * @param idIndex 同一ID内の通番。
 * @param idTotal 同一IDの総数。
 * @param boardName 板名。
 * @param boardId 板ID。
 * @param headerTextScale ヘッダーテキストの拡大率。
 * @param bodyTextScale 本文テキストの拡大率。
 * @param lineHeight 行間の倍率。
 * @param indentLevel インデントの段数。
 * @param replyFromNumbers 返信元番号の一覧。
 * @param isMyPost 自分の投稿かどうか。
 * @param dimmed 文字を薄く表示するかどうか。
 * @param searchQuery ハイライト対象の検索文字列。
 * @param onReplyFromClick 返信元番号のタップ時コールバック。
 * @param onReplyClick 本文内の返信番号タップ時コールバック。
 * @param onMenuReplyClick メニューから返信を選んだ時のコールバック。
 * @param onIdClick IDタップ時のコールバック。
 * @param onUrlClick URLタップ時のコールバック。
 * @param onThreadUrlClick スレッドURLタップ時のコールバック。
 * @param onImageClick 画像サムネイルタップ時のコールバック。
 * @param sharedTransitionScope 共有トランジションのスコープ。
 * @param animatedVisibilityScope アニメーション表示のスコープ。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ThreadPostUiModel,
    postNum: Int,
    idIndex: Int,
    idTotal: Int,
    boardName: String,
    boardId: Long,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeight: Float,
    indentLevel: Int = 0,
    replyFromNumbers: List<Int> = emptyList(),
    isMyPost: Boolean = false,
    dimmed: Boolean = false,
    searchQuery: String = "",
    onReplyFromClick: ((List<Int>) -> Unit),
    onReplyClick: ((Int) -> Unit)? = null,
    onMenuReplyClick: ((Int) -> Unit)? = null,
    onIdClick: ((String) -> Unit),
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onImageClick: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- 状態 ---
    val interactionState = rememberPostItemInteractionState()
    val dialogState = rememberPostItemDialogState()
    val scope = rememberCoroutineScope()

    // --- 表示 ---
    val bodyFontSize = MaterialTheme.typography.bodyMedium.fontSize * bodyTextScale
    val headerFontSize = MaterialTheme.typography.bodyMedium.fontSize * headerTextScale
    PostItemContainer(
        modifier = modifier,
        indentLevel = indentLevel,
        dimmed = dimmed,
        isPressed = interactionState.isContentPressed,
        scope = scope,
        onContentPressedChange = { interactionState.isContentPressed = it },
        onRequestMenu = { interactionState.isMenuExpanded = true },
        showMyPostIndicator = isMyPost,
    ) {
        PostItemHeader(
            uiModel = PostHeaderUiModel(
                header = post.header,
                postNum = postNum,
                idIndex = idIndex,
                idTotal = idTotal,
                replyFromNumbers = replyFromNumbers,
            ),
            headerTextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = headerFontSize),
            lineHeightEm = lineHeight,
            pressedHeaderPart = interactionState.pressedHeaderPart,
            scope = scope,
            onPressedHeaderPartChange = { interactionState.pressedHeaderPart = it },
            onContentPressedChange = { interactionState.isContentPressed = it },
            onRequestMenu = { interactionState.isMenuExpanded = true },
            onReplyFromClick = onReplyFromClick,
            onIdClick = onIdClick,
            onShowTextMenu = { text, type -> dialogState.showTextMenu(text = text, type = type) },
        )

        PostItemBody(
            post = post,
            bodyTextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize),
            lineHeightEm = lineHeight,
            searchQuery = searchQuery,
            pressedUrl = interactionState.pressedUrl,
            pressedReply = interactionState.pressedReply,
            scope = scope,
            onPressedUrlChange = { interactionState.pressedUrl = it },
            onPressedReplyChange = { interactionState.pressedReply = it },
            onContentPressedChange = { interactionState.isContentPressed = it },
            onRequestMenu = { interactionState.isMenuExpanded = true },
            onReplyClick = onReplyClick,
            onUrlClick = onUrlClick,
            onThreadUrlClick = onThreadUrlClick,
        )

        PostItemMedia(
            post = post,
            onImageClick = onImageClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

    // --- メニュー ---
    if (interactionState.isMenuExpanded) {
        PostMenuSheet(
            postNum = postNum,
            onReplyClick = {
                interactionState.isMenuExpanded = false
                onMenuReplyClick?.invoke(postNum)
            },
            onCopyClick = {
                interactionState.isMenuExpanded = false
                dialogState.showCopyDialog()
            },
            onNgClick = {
                interactionState.isMenuExpanded = false
                dialogState.showNgSelectDialog()
            },
            onDismiss = { interactionState.isMenuExpanded = false }
        )
    }

    // --- ダイアログ ---
    PostItemDialogs(
        post = post,
        postNum = postNum,
        boardName = boardName,
        boardId = boardId,
        scope = scope,
        dialogState = dialogState
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
private fun ReplyCardPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            PostItem(
                post = ThreadPostUiModel(
                    header = ThreadPostUiModel.Header(
                        name = "風吹けば名無し (ｵｰﾊﾟｲW ddad-g3Sx [2001:268:98f4:c793:*])",
                        email = "sage",
                        date = "1/21(月) 15:43:45.34",
                        id = "testnanjj",
                        beLoginId = "12345",
                        beRank = "PLT(2000)",
                        beIconUrl = "https://img.5ch.net/ico/1fu.gif",
                    ),
                    body = ThreadPostUiModel.Body(
                        content = "ガチで終わった模様"
                    ),
                ),
                postNum = 1,
                idIndex = 1,
                idTotal = 1,
                boardName = "board",
                boardId = 0L,
                headerTextScale = 0.85f,
                bodyTextScale = 1f,
                lineHeight = DEFAULT_THREAD_LINE_HEIGHT,
                searchQuery = "",
                isMyPost = true,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onReplyFromClick = {},
                onReplyClick = {},
                onMenuReplyClick = {},
                onIdClick = {},
                onUrlClick = {},
                onThreadUrlClick = {},
                onImageClick = {},
            )
        }
    }
}
