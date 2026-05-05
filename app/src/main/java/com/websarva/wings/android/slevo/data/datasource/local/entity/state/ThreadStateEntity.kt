package com.websarva.wings.android.slevo.data.datasource.local.entity.state

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.websarva.wings.android.slevo.data.model.ThreadId

/**
 * スレッドの最新レス数やタイトルなど、画面間で共有する客観状態を表す Room Entity。
 * 閲覧履歴に属する既読位置は保持せず、板一覧・タブ一覧・スレッド画面から同じ
 * `threadId` で参照される状態だけを保存する。
 */
@Entity(
    tableName = "thread_states",
    indices = [
        Index(value = ["boardId", "threadKey"]),
        Index(value = ["boardId"]),
        Index(value = ["boardUrl"]),
        Index(value = ["updatedAt"]),
    ]
)
data class ThreadStateEntity(
    @PrimaryKey val threadId: ThreadId,
    val boardId: Long,
    val boardUrl: String,
    val boardName: String,
    val threadKey: String,
    val title: String,
    val latestResCount: Int,
    val updatedAt: Long,
)
