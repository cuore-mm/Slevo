package com.websarva.wings.android.slevo.data.model

data class GroupedData<G : Groupable, I>(
    val group: G,
    val items: List<I>
)
