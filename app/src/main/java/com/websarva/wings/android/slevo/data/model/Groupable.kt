package com.websarva.wings.android.slevo.data.model

interface Groupable {
    val id: Long // グループの一意なID
    val name: String
    val colorName: String
    val sortOrder: Int
}
