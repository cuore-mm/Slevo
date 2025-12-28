package com.websarva.wings.android.slevo.data.model

data class ReplyInfo(
    val name: String,
    val email: String,
    val date: String,
    val id: String,
    val beLoginId: String = "",
    val beRank: String = "",
    val beIconUrl: String = "",
    val content: String,
    val momentum: Float = 0.0f,
    val urlFlags: Int = 0
) {
    companion object {
        const val HAS_IMAGE_URL = 1 shl 0
        const val HAS_THREAD_URL = 1 shl 1
        const val HAS_OTHER_URL = 1 shl 2
    }
}
