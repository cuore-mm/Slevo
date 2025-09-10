package com.websarva.wings.android.slevo.data.datasource.local.entity

import androidx.room.ColumnInfo

/**
 * スレッドの読み込み状態を保持する.
 */
data class ThreadReadState(
    @ColumnInfo(defaultValue = "0") val prevResCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastReadResNo: Int = 0,
    val firstNewResNo: Int? = null,
)
