package com.websarva.wings.android.slevo.ui.common.postdialog

/**
 * PostDialogの投稿成功時に画面側へ伝える情報。
 */
data class PostDialogSuccess(
    val resNum: Int?,
    val message: String,
    val name: String,
    val mail: String,
)
