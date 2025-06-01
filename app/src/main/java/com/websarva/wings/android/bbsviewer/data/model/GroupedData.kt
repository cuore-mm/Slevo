package com.websarva.wings.android.bbsviewer.data.model

data class GroupedData<G : Groupable, I>(
    val group: G,
    val items: List<I>
)
