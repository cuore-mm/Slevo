package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.BookmarkThreadDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.ThreadBookmarkGroupDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.GroupWithThreadBookmarks
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.ThreadBookmarkGroupEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.ThreadBookmarkWithGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadBookmarkRepository @Inject constructor(
    private val bookmarkThreadDao: BookmarkThreadDao,
    private val threadGroupDao: ThreadBookmarkGroupDao,
    private val gate: DatabaseWriteGate,
) {

    /** ブックマークの追加または更新 */
    suspend fun insertBookmark(bookmark: BookmarkThreadEntity) {
        gate.withWritePermit {
            bookmarkThreadDao.insertBookmark(bookmark)
        }
    }

    /** ブックマークの削除 (複合キー版) */
    suspend fun deleteBookmark(threadKey: String, boardUrl: String) {
        gate.withWritePermit {
            bookmarkThreadDao.deleteBookmark(threadKey, boardUrl)
        }
    }

    fun getBookmarkWithGroup(threadKey: String, boardUrl: String): Flow<ThreadBookmarkWithGroup?> {
        return bookmarkThreadDao.getBookmarkWithGroup(threadKey, boardUrl)
    }

    fun observeAllGroups(): Flow<List<ThreadBookmarkGroupEntity>> {
        return threadGroupDao.getAllGroupsSorted()
    }

    suspend fun addGroupAtEnd(name: String, colorName: String) {
        gate.withWritePermit {
            if (threadGroupDao.findByName(name) == null) {
                val nextOrder = threadGroupDao.getMaxSortOrder() + 1
                val newGroup = ThreadBookmarkGroupEntity(
                    name = name,
                    colorName = colorName,
                    sortOrder = nextOrder
                )
                threadGroupDao.insertGroup(newGroup)
            }
        }
    }

    suspend fun updateGroup(groupId: Long, name: String, colorName: String) {
        gate.withWritePermit {
            val existing = threadGroupDao.findByName(name)
            if (existing == null || existing.groupId == groupId) {
                threadGroupDao.updateGroupInfo(groupId, name, colorName)
            }
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        gate.withWritePermit {
            threadGroupDao.deleteGroupById(groupId)
        }
    }

    suspend fun updateGroupsOrder(groups: List<ThreadBookmarkGroupEntity>) {
        gate.withWritePermit {
            threadGroupDao.updateGroups(groups)
        }
    }

    // --- UI表示用の結合済みデータの取得 ---

    // 全てのグループと、それに属するスレッドブックマークのリストを取得 (ViewModelのinit用)
    // このメソッドは ThreadBookmarkGroupDao の getSortedGroupsWithThreadBookmarks に依存
    fun observeSortedGroupsWithThreadBookmarks(): Flow<List<GroupWithThreadBookmarks>> {
        return threadGroupDao.getSortedGroupsWithThreadBookmarks()
    }
}
