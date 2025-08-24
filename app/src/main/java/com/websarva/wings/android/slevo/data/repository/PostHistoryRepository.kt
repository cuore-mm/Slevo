package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostHistoryRepository @Inject constructor(
    private val dao: PostHistoryDao
) {
    suspend fun recordPost(
        content: String,
        date: Long,
        threadHistoryId: Long,
        boardId: Long,
        resNum: Int,
        name: String,
        email: String,
        postId: String
    ) {
        dao.insert(
            PostHistoryEntity(
                content = content,
                date = date,
                threadHistoryId = threadHistoryId,
                boardId = boardId,
                resNum = resNum,
                name = name,
                email = email,
                postId = postId
            )
        )
    }

    fun observeMyPostNumbers(threadHistoryId: Long): Flow<Set<Int>> =
        dao.observeResNums(threadHistoryId).map { it.toSet() }
}
