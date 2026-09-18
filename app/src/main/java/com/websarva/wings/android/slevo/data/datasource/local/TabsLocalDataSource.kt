package com.websarva.wings.android.slevo.data.datasource.local

import kotlinx.coroutines.flow.Flow

/**
 * Tabs関連の一覧ページとselected stable keyをPreferences DataStoreへ保存するデータソース。
 *
 * 板とスレッドのselected keyは独立したnullable値として扱い、null保存時は永続値を削除する。
 */
interface TabsLocalDataSource {
    fun observeLastSelectedTabsPage(): Flow<Int>
    suspend fun setLastSelectedTabsPage(page: Int)
    fun observeSelectedBoardTabKey(): Flow<String?>
    suspend fun setSelectedBoardTabKey(key: String?)
    fun observeSelectedThreadTabKey(): Flow<String?>
    suspend fun setSelectedThreadTabKey(key: String?)
}
