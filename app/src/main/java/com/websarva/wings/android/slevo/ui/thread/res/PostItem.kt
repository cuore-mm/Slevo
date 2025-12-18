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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.sheet.PostMenuSheet
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ThreadPostUiModel,
    postNum: Int,
    idIndex: Int,
    idTotal: Int,
    navController: NavHostController,
    tabsViewModel: TabsViewModel? = null,
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
    onReplyFromClick: ((List<Int>) -> Unit)? = null,
    onReplyClick: ((Int) -> Unit)? = null,
    onMenuReplyClick: ((Int) -> Unit)? = null,
    onIdClick: ((String) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val interactionState = rememberPostItemInteractionState()
    val dialogState = rememberPostItemDialogState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val bodyFontSize = MaterialTheme.typography.bodyMedium.fontSize * bodyTextScale
    val headerFontSize = MaterialTheme.typography.bodyMedium.fontSize * headerTextScale
    PostItemContainer(
        modifier = modifier,
        indentLevel = indentLevel,
        dimmed = dimmed,
        isPressed = interactionState.isContentPressed,
        scope = scope,
        haptic = haptic,
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
            haptic = haptic,
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
            haptic = haptic,
            onPressedUrlChange = { interactionState.pressedUrl = it },
            onPressedReplyChange = { interactionState.pressedReply = it },
            onContentPressedChange = { interactionState.isContentPressed = it },
            onRequestMenu = { interactionState.isMenuExpanded = true },
            onReplyClick = onReplyClick,
            navController = navController,
            tabsViewModel = tabsViewModel,
        )

        PostItemMedia(
            post = post,
            navController = navController,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

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
                navController = NavHostController(LocalContext.current),
                boardName = "board",
                boardId = 0L,
                headerTextScale = 0.85f,
                bodyTextScale = 1f,
                lineHeight = DEFAULT_THREAD_LINE_HEIGHT,
                searchQuery = "",
                isMyPost = true,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
