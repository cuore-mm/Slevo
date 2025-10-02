package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostIdentityHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostLastIdentityDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostLastIdentityEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostHistoryRepository @Inject constructor(
    private val dao: PostHistoryDao,
    private val identityDao: PostIdentityHistoryDao,
    private val lastIdentityDao: PostLastIdentityDao
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
        if (boardId != 0L) {
            lastIdentityDao.upsert(
                PostLastIdentityEntity(
                    boardId = boardId,
                    name = name,
                    email = email,
                    updatedAt = now
                )
            )
        }
        recordIdentityIfNeeded(boardId, PostIdentityType.NAME, name, now)
        recordIdentityIfNeeded(boardId, PostIdentityType.EMAIL, email, now)
    }

    fun observeMyPostNumbers(threadHistoryId: Long): Flow<Set<Int>> =
        dao.observeResNums(threadHistoryId).map { it.toSet() }

    fun observeIdentityHistories(boardId: Long, type: PostIdentityType): Flow<List<String>> =
        identityDao.observeValues(boardId, type.name)

    suspend fun recordIdentity(boardId: Long, name: String?, email: String?) {
        val now = System.currentTimeMillis()
        if (boardId != 0L) {
            lastIdentityDao.upsert(
                PostLastIdentityEntity(
                    boardId = boardId,
                    name = name ?: "",
                    email = email ?: "",
                    updatedAt = now
                )
            )
        }
        recordIdentityIfNeeded(boardId, PostIdentityType.NAME, name, now)
        recordIdentityIfNeeded(boardId, PostIdentityType.EMAIL, email, now)
    }

    suspend fun getLastIdentity(boardId: Long): PostLastIdentity? {
        if (boardId == 0L) return null
        return lastIdentityDao.findByBoardId(boardId)?.let {
            PostLastIdentity(
                name = it.name,
                email = it.email
            )
        }
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

data class PostLastIdentity(
    val name: String,
    val email: String
)
