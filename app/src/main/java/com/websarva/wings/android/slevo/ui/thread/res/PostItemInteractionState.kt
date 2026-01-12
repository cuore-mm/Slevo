package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 投稿アイテムの押下状態を保持する。
 *
 * URL/返信/ヘッダーの押下状態をまとめて管理する。
 */
@Stable
internal class PostItemInteractionState {
    var isContentPressed by mutableStateOf(false)
    var pressedUrl by mutableStateOf<String?>(null)
    var pressedReply by mutableStateOf<String?>(null)
    var pressedHeaderPart by mutableStateOf<PostHeaderPart?>(null)
    var isTapHandled by mutableStateOf(false)
}

@Composable
internal fun rememberPostItemInteractionState(): PostItemInteractionState {
    return remember { PostItemInteractionState() }
}
