package com.websarva.wings.android.bbsviewer.data.util

object DataUsageTracker {
    @Volatile
    var datBytes: Long = 0
        private set

    fun addBytes(size: Long) {
        datBytes += size
    }
}
