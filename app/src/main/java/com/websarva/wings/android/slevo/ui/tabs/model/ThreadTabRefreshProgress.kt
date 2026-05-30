package com.websarva.wings.android.slevo.ui.tabs.model

/**
 * スレッドタブ更新処理の進捗状態を表す。
 *
 * 完了件数と総件数から UI 表示用の進捗率を導出する。
 */
data class ThreadTabRefreshProgress(
    val completedCount: Int,
    val totalCount: Int,
) {
    /**
     * 進捗率を 0f〜1f の範囲で返す。
     */
    val progress: Float
        get() = if (totalCount <= 0) {
            0f
        } else {
            completedCount.toFloat() / totalCount.toFloat()
        }
}
