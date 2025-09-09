package com.websarva.wings.android.slevo.data.datasource.local.entity

/**
 * スレッドの読み込み状態を保持する.
 */
data class ThreadReadState(
    val prevResCount: Int = 0,
    val lastReadResNo: Int = 0,
    val firstNewResNo: Int? = null,
)
