package com.websarva.wings.android.slevo.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.websarva.wings.android.slevo.data.model.ThreadId

/**
 * 開いている板タブの表示情報とスクロール位置を保持する Room Entity。
 * 板タブ一覧の復元に使うため、板情報、並び順、最後のスクロール位置を保存する。
 */
@Entity(tableName = "open_board_tabs")
data class OpenBoardTabEntity(
    @PrimaryKey val boardUrl: String,
    val boardId: Long,
    val boardName: String,
    val serviceName: String,
    val sortOrder: Int,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)

/**
 * 開いているスレッドタブのタブ固有状態を保持する Room Entity。
 * タイトル、レス数、既読位置は保持せず、表示時に `thread_states` と `thread_histories` から合成する。
 */
@Entity(tableName = "open_thread_tabs")
data class OpenThreadTabEntity(
    @PrimaryKey val threadId: ThreadId,
    val sortOrder: Int,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)
