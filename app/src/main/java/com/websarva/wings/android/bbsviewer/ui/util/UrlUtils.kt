package com.websarva.wings.android.bbsviewer.ui.util

fun keyToDatUrl(boardUrl:String, key: String): String {
    return "${boardUrl}dat/${key}.dat"
}
