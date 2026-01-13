package com.websarva.wings.android.slevo.ui.thread.state

/**
 * スレ画面で表示するための投稿 UI モデル。
 *
 * - [header]: 投稿者情報など、ヘッダー領域の情報
 * - [body]: 本文など、ボディ領域の情報
 * - [meta]: 勢い・URLフラグなど表示補助の情報
 */
data class ThreadPostUiModel(
    val header: Header,
    val body: Body,
    val meta: Meta = Meta(),
) {
    data class Header(
        val name: String,
        val email: String,
        val date: String,
        val id: String,
        val beLoginId: String = "",
        val beRank: String = "",
        val beIconUrl: String = "",
    )

    data class Body(
        val content: String,
    )

    data class Meta(
        val momentum: Float = 0.0f,
        val urlFlags: Int = 0,
    )
}

