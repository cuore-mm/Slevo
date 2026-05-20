package com.websarva.wings.android.slevo.di

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
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
) : RoomDatabase.Callback() {

    // データベース操作用のコルーチンスコープ
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * データベースが最初に作成されたときに一度だけ呼び出される
     */
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        applicationScope.launch {
            populateInitialData()
        }
    }

    /**
     * データベースが開かれたタイミングで少量の孤立スレッド客観状態を削除する。
     * 起動処理を阻害しないよう、削除件数上限は Repository 側の起動時用設定を使用する。
     */
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        applicationScope.launch {
            collectStartupThreadStateGarbage(db)
        }
    }

    /**
     * 起動時に古い孤立 `thread_states` を少量だけ削除する。
     * RoomDatabase 構築中の循環参照を避けるため、Callback 内では SupportSQLiteDatabase を直接使う。
     */
    private fun collectStartupThreadStateGarbage(db: SupportSQLiteDatabase) {
        val updatedBefore = System.currentTimeMillis() - THREAD_STATE_GARBAGE_TTL_MILLIS
        val targets = mutableListOf<String>()
        db.query(
            "SELECT s.threadId FROM thread_states s " +
                "LEFT JOIN open_thread_tabs t ON t.threadId = s.threadId " +
                "LEFT JOIN thread_histories h ON h.threadId = s.threadId " +
                "LEFT JOIN bookmark_threads b ON b.boardUrl = s.boardUrl AND b.threadKey = s.threadKey " +
                "LEFT JOIN thread_summaries ts ON ts.boardId = s.boardId " +
                "AND ts.threadId = s.threadKey " +
                "WHERE s.updatedAt < $updatedBefore " +
                "AND t.threadId IS NULL " +
                "AND h.threadId IS NULL " +
                "AND b.threadKey IS NULL " +
                "AND ts.threadId IS NULL " +
                "ORDER BY s.updatedAt ASC LIMIT $STARTUP_THREAD_STATE_GARBAGE_LIMIT"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                targets += cursor.getString(0)
            }
        }
        targets.forEach { threadId ->
            db.execSQL("DELETE FROM thread_states WHERE threadId = ?", arrayOf(threadId))
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

    /**
     * 起動時 GC の保持期間と削除件数上限を保持する定数置き場。
     */
    companion object {
        private const val THREAD_STATE_GARBAGE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val STARTUP_THREAD_STATE_GARBAGE_LIMIT = 20
    }
}
