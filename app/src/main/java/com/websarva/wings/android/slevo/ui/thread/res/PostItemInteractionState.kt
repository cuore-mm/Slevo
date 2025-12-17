package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
internal class PostItemInteractionState {
    var isContentPressed by mutableStateOf(false)
    var pressedUrl by mutableStateOf<String?>(null)
    var pressedReply by mutableStateOf<String?>(null)
    var pressedHeaderPart by mutableStateOf<String?>(null)
    var isMenuExpanded by mutableStateOf(false)
}

@Composable
internal fun rememberPostItemInteractionState(): PostItemInteractionState {
    return remember { PostItemInteractionState() }
}

