package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 検索中と通常時の下部コントローラーを切り替える。
 *
 * 受け取ったmodifierは通常表示と検索表示の両方へ適用し、外部Pagerのドラッグ領域を維持する。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BbsRouteBottomBar(
    modifier: Modifier = Modifier,
    isSearchMode: Boolean,
    onCloseSearch: () -> Unit,
    animationLabel: String,
    searchContent: @Composable (modifier: Modifier, closeSearch: () -> Unit) -> Unit,
    defaultContent: @Composable (modifier: Modifier) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Material 3 の BottomAppBar が navigation bar Insets を所有し、検索時だけ IME を追加する。
    val searchModifier = Modifier.imePadding()
    val defaultModifier = Modifier

    val closeSearch: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onCloseSearch()
    }

    BackHandler(enabled = isSearchMode) {
        closeSearch()
    }

    AnimatedContent(
        modifier = modifier,
        targetState = isSearchMode,
        transitionSpec = {
            slideInVertically { it } + fadeIn() togetherWith
                    slideOutVertically { it } + fadeOut()
        },
        label = animationLabel,
    ) { searchMode ->
        if (searchMode) {
            searchContent(searchModifier, closeSearch)
        } else {
            defaultContent(defaultModifier)
        }
    }
}
