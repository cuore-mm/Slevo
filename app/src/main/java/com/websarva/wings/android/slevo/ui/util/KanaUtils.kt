package com.websarva.wings.android.slevo.ui.util

fun String.toHiragana(): String = buildString {
    for (ch in this@toHiragana) {
        append(
            if (ch in 'ァ'..'ヶ') {
                (ch.code - 0x60).toChar()
            } else {
                ch
            }
        )
    }
}

