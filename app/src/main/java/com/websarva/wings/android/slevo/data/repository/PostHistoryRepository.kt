package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostIdentityHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostHistoryRepository @Inject constructor(
    private val dao: PostHistoryDao,
    private val identityDao: PostIdentityHistoryDao
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

        val now = System.currentTimeMillis()
        recordIdentityIfNeeded(boardId, PostIdentityType.NAME, name, now)
        recordIdentityIfNeeded(boardId, PostIdentityType.EMAIL, email, now)
    }

    fun observeMyPostNumbers(threadHistoryId: Long): Flow<Set<Int>> =
        dao.observeResNums(threadHistoryId).map { it.toSet() }

    suspend fun recordIdentity(boardId: Long, name: String?, email: String?) {
        val now = System.currentTimeMillis()
        recordIdentityIfNeeded(boardId, PostIdentityType.NAME, name, now)
        recordIdentityIfNeeded(boardId, PostIdentityType.EMAIL, email, now)
    }

    private suspend fun recordIdentityIfNeeded(
        boardId: Long,
        type: PostIdentityType,
        rawValue: String?,
        timestamp: Long
    ) {
        val value = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return
        identityDao.upsert(
            PostIdentityHistoryEntity(
                boardId = boardId,
                type = type.name,
                value = value,
                lastUsedAt = timestamp
            )
        )
        val ids = identityDao.findIdsOrdered(boardId, type.name)
        if (ids.size > MAX_HISTORY_COUNT) {
            val deleteIds = ids.drop(MAX_HISTORY_COUNT)
            if (deleteIds.isNotEmpty()) {
                identityDao.deleteByIds(deleteIds)
            }
        }
    }

    companion object {
        private const val MAX_HISTORY_COUNT = 3
    }
}
