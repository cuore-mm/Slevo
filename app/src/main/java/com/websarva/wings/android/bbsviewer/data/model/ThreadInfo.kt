package com.websarva.wings.android.bbsviewer.data.model

data class ThreadInfo(
    val title: String= "",
    val key: String= "",
    val url: String= "",
    val datUrl: String= "",
    val resCount: Int= 0,
    val date: ThreadDate = ThreadDate(0, 0, 0, 0, 0, ""),
    val momentum: Double = 0.0,
    val isVisited: Boolean = false,
    val newResCount: Int = 0,
    val isNew: Boolean = false,
)
