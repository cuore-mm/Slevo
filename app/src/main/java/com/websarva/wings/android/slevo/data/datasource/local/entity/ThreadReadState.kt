package com.websarva.wings.android.slevo.data.datasource.local.entity

data class ThreadReadState(
    val prevResCount: Int = 0,
    val lastReadResNo: Int = 0,
    val firstNewResNo: Int? = null
)
