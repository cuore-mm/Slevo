package com.websarva.wings.android.slevo.data.datasource.local.entity.history

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState

@Entity(
    tableName = "thread_histories",
    indices = [Index(value = ["threadId"], unique = true)]
)
data class ThreadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: ThreadId,
    val boardUrl: String,
    val boardId: Long,
    val boardName: String,
    val title: String,
    val resCount: Int = 0,
    @Embedded val readState: ThreadReadState = ThreadReadState()
)
