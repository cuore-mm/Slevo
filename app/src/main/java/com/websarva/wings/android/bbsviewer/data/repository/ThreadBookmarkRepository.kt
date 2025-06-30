package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithThreadBookmarks
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadBookmarkWithGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadBookmarkRepository @Inject constructor(
    private val bookmarkThreadDao: BookmarkThreadDao,
    private val threadGroupDao: ThreadBookmarkGroupDao
) {

    // ブックマークの追加または更新
    suspend fun insertBookmark(bookmark: BookmarkThreadEntity) {
        bookmarkThreadDao.insertBookmark(bookmark)
    }

    // ブックマークの削除 (複合キー版)
    suspend fun deleteBookmark(threadKey: String, boardUrl: String) {
        bookmarkThreadDao.deleteBookmark(threadKey, boardUrl)
    }

    // 指定したスレッドのブックマーク情報をグループ付きで取得
    fun getBookmarkWithGroup(threadKey: String, boardUrl: String): Flow<ThreadBookmarkWithGroup?> {
        return bookmarkThreadDao.getBookmarkWithGroup(threadKey, boardUrl)
    }

    fun observeAllGroups(): Flow<List<ThreadBookmarkGroupEntity>> {
        return threadGroupDao.getAllGroupsSorted()
    }

    suspend fun addGroupAtEnd(name: String, colorHex: String) {
        val nextOrder = threadGroupDao.getMaxSortOrder() + 1
        val newGroup = ThreadBookmarkGroupEntity(
            name = name,
            colorHex = colorHex,
            sortOrder = nextOrder
        )
        threadGroupDao.insertGroup(newGroup)
    }

    suspend fun updateGroup(groupId: Long, name: String, colorHex: String) {
        threadGroupDao.updateGroupInfo(groupId, name, colorHex)
    }

    suspend fun deleteGroup(groupId: Long) {
        threadGroupDao.deleteGroupById(groupId)
    }

    suspend fun updateGroupsOrder(groups: List<ThreadBookmarkGroupEntity>) {
        threadGroupDao.updateGroups(groups) // DAOに一括更新メソッドがある前提
    }

    // 必要に応じてグループの名前変更や色変更、削除などのメソッドを追加

    // --- UI表示用の結合済みデータの取得 ---

    // 全てのグループと、それに属するスレッドブックマークのリストを取得 (ViewModelのinit用)
    // このメソッドは ThreadBookmarkGroupDao の getSortedGroupsWithThreadBookmarks に依存
    fun observeSortedGroupsWithThreadBookmarks(): Flow<List<GroupWithThreadBookmarks>> {
        return threadGroupDao.getSortedGroupsWithThreadBookmarks()
    }
}
