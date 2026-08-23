package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.model.PostReceipt

/**
 * PostDialogの投稿成功時に画面側へ伝える情報。
 */
data class PostDialogSuccess(
    val receipt: PostReceipt = PostReceipt(),
    val message: String,
    val name: String,
    val mail: String,
    val baseResCount: Int? = null,
)
