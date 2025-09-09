package com.websarva.wings.android.slevo.data.model

@JvmInline
value class ThreadId(val value: String) {
    companion object {
        fun of(host: String, board: String, threadKey: String) =
            ThreadId("$host/$board/$threadKey")
    }
}
