package com.websarva.wings.android.slevo.data.repository.fake

import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardCategoryCrossRefDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryWithBoardCount
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.ServiceWithBoardCount
import com.websarva.wings.android.slevo.data.datasource.local.dao.state.ThreadStateDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardCategoryCrossRef
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardWithCategories
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.CategoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.state.ThreadStateEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * テスト用の `BbsServiceDao` fake。
 *
 * ドメインは unique 制約がある前提で、`insertService` は既存ドメインに対して
 * -1L（`OnConflictStrategy.IGNORE` の挙動）を返す。
 */
class FakeBbsServiceDao : BbsServiceDao {
    private val nextId = mutableMapOf<String, Long>()
    private val services = MutableStateFlow<List<BbsServiceEntity>>(emptyList())
    private var autoIncrement = 1L

    override suspend fun insertService(service: BbsServiceEntity): Long {
        val existing = services.value.firstOrNull { it.domain == service.domain }
        if (existing != null) return -1L
        val assignedId = autoIncrement++
        nextId[service.domain] = assignedId
        services.value = services.value + service.copy(serviceId = assignedId)
        return assignedId
    }

    override suspend fun updateServiceMeta(serviceId: Long, displayName: String?, menuUrl: String?) {
        services.value = services.value.map {
            if (it.serviceId == serviceId) it.copy(displayName = displayName, menuUrl = menuUrl) else it
        }
    }

    override fun getServicesWithBoardCount(): Flow<List<ServiceWithBoardCount>> =
        services.map { list -> list.map { ServiceWithBoardCount(it, 0) } }

    override suspend fun getById(id: Long): BbsServiceEntity? =
        services.value.firstOrNull { it.serviceId == id }

    override suspend fun deleteById(id: Long) {
        services.value = services.value.filterNot { it.serviceId == id }
    }

    override suspend fun findByDomain(domain: String): BbsServiceEntity? =
        services.value.firstOrNull { it.domain == domain }

    fun snapshot(): List<BbsServiceEntity> = services.value
}

/**
 * テスト用の `CategoryDao` fake。
 */
class FakeCategoryDao : CategoryDao {
    private val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private var autoIncrement = 1L

    override suspend fun insertCategory(category: CategoryEntity): Long {
        val assignedId = autoIncrement++
        categories.value = categories.value + category.copy(categoryId = assignedId)
        return assignedId
    }

    override suspend fun clearForService(serviceId: Long) {
        categories.value = categories.value.filterNot { it.serviceId == serviceId }
    }

    override fun getCategoriesWithBoardCount(serviceId: Long): Flow<List<CategoryWithBoardCount>> =
        categories.map { list -> list.filter { it.serviceId == serviceId }.map { CategoryWithBoardCount(it, 0) } }

    override fun getCategoriesForService(serviceId: Long): Flow<List<CategoryEntity>> =
        categories.map { list -> list.filter { it.serviceId == serviceId } }

    fun snapshot(): List<CategoryEntity> = categories.value
}

/**
 * テスト用の `BoardDao` fake。
 *
 * URL 衝突時は `insertBoard` が -1L を返し、`findBoardIdByUrl` で URL から既存 boardId を取り出せる。
 */
class FakeBoardDao : BoardDao {
    private val boards = MutableStateFlow<List<BoardEntity>>(emptyList())
    private var autoIncrement = 1L

    override suspend fun insertBoard(board: BoardEntity): Long {
        val existing = boards.value.firstOrNull { it.serviceId == board.serviceId && it.url == board.url }
        if (existing != null) return -1L
        val assignedId = autoIncrement++
        boards.value = boards.value + board.copy(boardId = assignedId)
        return assignedId
    }

    override suspend fun findBoardIdByUrl(url: String): Long =
        boards.value.firstOrNull { it.url == url }?.boardId ?: -1L

    override suspend fun findBoardByUrl(boardUrl: String): BoardEntity? =
        boards.value.firstOrNull { it.url == boardUrl }

    override suspend fun findBoardById(boardId: Long): BoardEntity? =
        boards.value.firstOrNull { it.boardId == boardId }

    override suspend fun findBoardsByUrlPattern(pattern: String): List<BoardEntity> =
        boards.value.filter { it.url.like(pattern) }

    override suspend fun clearForService(serviceId: Long) {
        boards.value = boards.value.filterNot { it.serviceId == serviceId }
    }

    override fun getBoardsForService(serviceId: Long): Flow<List<BoardEntity>> =
        boards.map { list -> list.filter { it.serviceId == serviceId } }

    override fun getBoardWithCategories(boardId: Long): Flow<BoardWithCategories> {
        TODO("Not needed in current tests")
    }

    override fun getBoardsByIds(ids: List<Long>): Flow<List<BoardEntity>> =
        boards.map { list -> list.filter { it.boardId in ids } }

    override fun getAllBoards(): Flow<List<BoardEntity>> = boards

    fun snapshot(): List<BoardEntity> = boards.value
}

/**
 * テスト用の `BoardCategoryCrossRefDao` fake。
 */
class FakeBoardCategoryCrossRefDao : BoardCategoryCrossRefDao {
    private val crossRefs = MutableStateFlow<List<BoardCategoryCrossRef>>(emptyList())

    override suspend fun insert(ref: BoardCategoryCrossRef) {
        if (crossRefs.value.contains(ref)) return
        crossRefs.value = crossRefs.value + ref
    }

    override suspend fun clearForBoard(boardId: Long) {
        crossRefs.value = crossRefs.value.filterNot { it.boardId == boardId }
    }

    override fun getCrossRefsForCategory(categoryId: Long): Flow<List<BoardCategoryCrossRef>> =
        crossRefs.map { list -> list.filter { it.categoryId == categoryId } }

    override fun getBoardIdsForCategory(categoryId: Long): Flow<List<Long>> =
        crossRefs.map { list -> list.filter { it.categoryId == categoryId }.map { it.boardId } }

    fun snapshot(): List<BoardCategoryCrossRef> = crossRefs.value
}

/**
 * テスト用の `ThreadStateDao` fake。
 *
 * `upsertKeepingMaxResCount` は insertIgnore + updateKeepingMaxResCount を順に呼ぶことで
 * 「既存があれば最新レス数で上書き、なければ insert」を再現する。
 * `deleteGarbage` は現在時刻より古いスレッドを `limit` 件まで削除する。
 */
class FakeThreadStateDao : ThreadStateDao() {
    private val states = MutableStateFlow<List<ThreadStateEntity>>(emptyList())

    override suspend fun find(threadId: ThreadId): ThreadStateEntity? =
        states.value.firstOrNull { it.threadId == threadId }

    override fun observe(threadId: ThreadId): Flow<ThreadStateEntity?> =
        states.map { list -> list.firstOrNull { it.threadId == threadId } }

    override fun observeByBoard(boardId: Long): Flow<List<ThreadStateEntity>> =
        states.map { list -> list.filter { it.boardId == boardId }.sortedByDescending { it.updatedAt } }

    override suspend fun findByBoard(boardId: Long): List<ThreadStateEntity> =
        states.value.filter { it.boardId == boardId }.sortedByDescending { it.updatedAt }

    override suspend fun findGarbageCandidates(updatedBefore: Long, limit: Int): List<ThreadId> =
        states.value
            .filter { it.updatedAt < updatedBefore }
            .sortedBy { it.updatedAt }
            .take(limit.coerceAtLeast(0))
            .map { it.threadId }

    override suspend fun deleteByThreadIds(threadIds: List<ThreadId>) {
        val targetSet = threadIds.toSet()
        states.value = states.value.filterNot { it.threadId in targetSet }
    }

    override fun observeForOpenTabs(): Flow<List<ThreadStateEntity>> = states

    override suspend fun insertIgnore(entity: ThreadStateEntity): Long {
        if (states.value.any { it.threadId == entity.threadId }) {
            return -1L
        }
        states.value = states.value + entity
        return 1L
    }

    override suspend fun updateKeepingMaxResCount(
        threadId: ThreadId,
        boardId: Long,
        boardUrl: String,
        boardName: String,
        threadKey: String,
        title: String,
        latestResCount: Int,
        updatedAt: Long,
    ) {
        states.value = states.value.map { entity ->
            if (entity.threadId == threadId) {
                entity.copy(
                    boardId = boardId,
                    boardUrl = boardUrl,
                    boardName = boardName,
                    threadKey = threadKey,
                    title = title,
                    latestResCount = maxOf(entity.latestResCount, latestResCount),
                    updatedAt = updatedAt,
                )
            } else {
                entity
            }
        }
    }

    fun snapshot(): List<ThreadStateEntity> = states.value
}

/**
 * `BbsServiceRepository` 用に fake DAO をまとめたホルダー。
 */
data class BbsRepositoryFakes(
    val serviceDao: FakeBbsServiceDao,
    val categoryDao: FakeCategoryDao,
    val boardDao: FakeBoardDao,
    val crossRefDao: FakeBoardCategoryCrossRefDao,
) {
    companion object {
        fun create(): BbsRepositoryFakes = BbsRepositoryFakes(
            serviceDao = FakeBbsServiceDao(),
            categoryDao = FakeCategoryDao(),
            boardDao = FakeBoardDao(),
            crossRefDao = FakeBoardCategoryCrossRefDao(),
        )
    }
}

private fun String.like(pattern: String): Boolean {
    val regex = pattern
        .replace("%", ".*")
        .replace("_", ".")
        .toRegex()
    return regex.matches(this)
}
