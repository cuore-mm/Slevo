package com.websarva.wings.android.slevo.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.websarva.wings.android.slevo.ui.util.isThreeButtonNavigation

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BbsRouteBottomBar(
    isSearchMode: Boolean,
    onCloseSearch: () -> Unit,
    animationLabel: String,
    searchContent: @Composable (modifier: Modifier, closeSearch: () -> Unit) -> Unit,
    defaultContent: @Composable (modifier: Modifier) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val isThreeButtonBar = remember { isThreeButtonNavigation(context) }

    val searchModifier = if (isThreeButtonBar) {
        Modifier
            .navigationBarsPadding()
            .imePadding()
    } else {
        Modifier.imePadding()
    }

    val defaultModifier = Modifier.navigationBarsPadding()

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
            searchContent(searchModifier, closeSearch)
        } else {
            defaultContent(defaultModifier)
        }
    }
}
