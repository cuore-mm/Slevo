package com.websarva.wings.android.slevo.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BbsRouteBottomBar(
    isSearchMode: Boolean,
    onCloseSearch: () -> Unit,
    animationLabel: String,
    searchContent: @Composable (closeSearch: () -> Unit) -> Unit,
    defaultContent: @Composable () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val closeSearch: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onCloseSearch()
    }

    BackHandler(enabled = isSearchMode) {
        closeSearch()
    }

    AnimatedContent(
        targetState = isSearchMode,
        transitionSpec = {
            slideInVertically { it } + fadeIn() togetherWith
                    slideOutVertically { it } + fadeOut()
        },
        label = animationLabel,
    ) { searchMode ->
        if (searchMode) {
            searchContent(closeSearch)
        } else {
            defaultContent()
        }
    }
}
