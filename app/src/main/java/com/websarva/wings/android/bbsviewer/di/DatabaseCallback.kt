package com.websarva.wings.android.bbsviewer.di

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.repository.BbsServiceRepository
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.ThreadBookmarkRepository
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
    @ApplicationContext private val context: Context,
    private val bbsServiceRepositoryProvider: Provider<BbsServiceRepository>,
    private val bookmarkBoardRepositoryProvider: Provider<BookmarkBoardRepository>,
    private val bookmarkThreadRepositoryProvider: Provider<ThreadBookmarkRepository>
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
     * 初期データをデータベースに追加する
     */
    private suspend fun populateInitialData() {
        // デフォルトの5chサービスを登録
        bbsServiceRepositoryProvider.get().addOrUpdateService("https://menu.5ch.net/bbsmenu.html")

        // 文字列リソースから「お気に入り」を取得
        val favoriteGroupName = context.getString(R.string.bookmark) // ← Context を使って文字列を取得

        // デフォルトのお気に入りグループを登録
        bookmarkBoardRepositoryProvider.get().addGroupAtEnd(
            name = favoriteGroupName, // ← 取得した文字列を使用
            colorHex = "#FFFF00" // 黄色のHEXコード
        )

        val threadFavoriteGroupName = context.getString(R.string.bookmark)
        bookmarkThreadRepositoryProvider.get().addGroupAtEnd(
            name = threadFavoriteGroupName,
            colorHex = "#FFFF00"
        )
    }
}
