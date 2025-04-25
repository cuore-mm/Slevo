package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkThreadDao: BookmarkThreadDao
) {
    // 全てのブックマークをFlowで取得する
    fun getAllBookmarks(): Flow<List<BookmarkThreadEntity>> = bookmarkThreadDao.getAllBookmarks()

    // ブックマークの追加または更新
    suspend fun insertBookmark(bookmark: BookmarkThreadEntity) {
        bookmarkThreadDao.insertBookmark(bookmark)
    }

    // ブックマークの削除
    suspend fun deleteBookmark(bookmark: BookmarkThreadEntity) {
        bookmarkThreadDao.deleteBookmark(bookmark)
    }

    // 指定したIDのブックマークを取得
    suspend fun getBookmarkById(id: String): BookmarkThreadEntity? {
        return bookmarkThreadDao.getBookmarkById(id)
    }
}
