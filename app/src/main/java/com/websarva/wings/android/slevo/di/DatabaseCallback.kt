package com.websarva.wings.android.slevo.di

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreCompletionChecker
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
            runPendingRestoreCompletionCheckerWithBoundary(pendingRestoreCompletionCheckerProvider)
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

/**
 * completion checker の起動境界を担当する。
 *
 * checker の non-throwing contract が将来崩れても DB open 後の coroutine を失敗させず、
 * marker を recovery authority とした次回 cold start に処理を委ねる。structured cancellation
 * を維持するため [CancellationException] だけは再 throw する。
 */
internal suspend fun runPendingRestoreCompletionCheckerWithBoundary(
    provider: Provider<PendingRestoreCompletionChecker>,
    logException: (String) -> Unit = ::logPendingRestoreCompletionCheckerException,
) {
    try {
        provider.get().runIfNeeded()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val exceptionType = e::class.java.simpleName
            .filter { it.isLetterOrDigit() || it == '_' }
            .ifBlank { "Exception" }
        logException("pending restore completion checker failed: $exceptionType")
    }
}

/** operational exception を機密情報を含めずログへ記録する。 */
private fun logPendingRestoreCompletionCheckerException(message: String) {
    try {
        Log.e("DatabaseCallback", message)
    } catch (_: RuntimeException) {
        // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
    }
}
