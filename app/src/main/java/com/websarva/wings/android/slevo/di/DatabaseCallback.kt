package com.websarva.wings.android.slevo.di

import android.content.Context
import androidx.room.RoomDatabase
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreCompletionChecker
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Room データベース作成時に初期データを投入するためのコールバック
 */
@Singleton
class DatabaseCallback @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bbsServiceRepositoryProvider: Provider<BbsServiceRepository>,
    private val bookmarkBoardRepositoryProvider: Provider<BookmarkBoardRepository>,
    private val bookmarkThreadRepositoryProvider: Provider<ThreadBookmarkRepository>,
    private val threadStateRepositoryProvider: Provider<ThreadStateRepository>,
    private val pendingRestoreCompletionCheckerProvider: Provider<PendingRestoreCompletionChecker>,
) : RoomDatabase.Callback() {

    // データベース操作用のコルーチンスコープ
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * データベースが最初に作成されたときに一度だけ呼び出される
     */
    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onCreate(db)
        applicationScope.launch {
            populateInitialData()
        }
    }

    /**
     * データベースが開かれたタイミングで少量の孤立スレッド客観状態を削除する。
     * 起動処理を阻害しないよう、削除件数上限は Repository 側の起動時用設定を使用する。
     *
     * Phase 2 (add-database-write-gate) で `Provider<ThreadStateRepository>` 経由の
     * `collectStartupGarbage()` に切り替えた。direct `SupportSQLiteDatabase.execSQL` は
     * 廃止している。
     */
    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onOpen(db)
        applicationScope.launch {
            threadStateRepositoryProvider.get().collectStartupGarbage()
        }
        applicationScope.launch {
            pendingRestoreCompletionCheckerProvider.get().runIfNeeded()
        }
    }

    /**
     * 初期データをデータベースに追加する
     */
    private suspend fun populateInitialData() {
        // デフォルトの5chサービスを登録
        bbsServiceRepositoryProvider.get().addOrUpdateService("https://menu.5ch.io/bbsmenu.html")

        // 文字列リソースから「お気に入り」を取得
        val bookmarkGroupName = context.getString(R.string.bookmark) // ← Context を使って文字列を取得

        // デフォルトのお気に入りグループを登録
        bookmarkBoardRepositoryProvider.get().addGroupAtEnd(
            name = bookmarkGroupName, // ← 取得した文字列を使用
            colorName = BookmarkColor.YELLOW.value
        )

        val threadBookmarkGroupName = context.getString(R.string.bookmark)
        bookmarkThreadRepositoryProvider.get().addGroupAtEnd(
            name = threadBookmarkGroupName,
            colorName = BookmarkColor.YELLOW.value
        )
    }
}
