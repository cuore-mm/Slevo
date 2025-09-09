package com.websarva.wings.android.slevo.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryEntity

/**
 * OpenThreadTabEntity と ThreadHistoryEntity を結合したデータクラス
 */
data class OpenThreadTabWithHistory(
    @Embedded val tab: OpenThreadTabEntity,
    @Relation(
        parentColumn = "threadId",
        entityColumn = "threadId"
    )
    val history: ThreadHistoryEntity
)
