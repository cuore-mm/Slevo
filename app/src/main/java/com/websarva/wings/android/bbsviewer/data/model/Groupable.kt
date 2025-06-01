package com.websarva.wings.android.bbsviewer.data.model

interface Groupable {
    val id: Long // グループの一意なID
    val name: String
    val colorHex: String
    val sortOrder: Int
}
