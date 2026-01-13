package com.websarva.wings.android.slevo.ui.thread.state

/**
 * スレッド更新単位でレス範囲を表すグループ定義。
 *
 * startResNo〜endResNo がこの更新で取得したレス範囲で、
 * prevResCount は更新前に存在していた総レス数を示す。
 */
data class ThreadPostGroup(
    val startResNo: Int,
    val endResNo: Int,
    val prevResCount: Int,
)
