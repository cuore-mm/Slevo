package com.websarva.wings.android.bbsviewer.data.model

data class ThreadInfo(
    val title: String= "",
    val key: String= "",
    val resCount: Int= 0,
    val date: ThreadDate = ThreadDate(0, 0, 0, 0, 0, ""),
)
