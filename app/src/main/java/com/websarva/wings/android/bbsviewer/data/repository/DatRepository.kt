package com.websarva.wings.android.bbsviewer.data.repository

import android.util.Log
import com.websarva.wings.android.bbsviewer.data.datasource.remote.DatRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.util.parseDat
import com.websarva.wings.android.bbsviewer.ui.thread.ReplyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import javax.inject.Inject

class DatRepository @Inject constructor(
    private val remoteDataSource: DatRemoteDataSource
) {
    /**
     * 指定されたURLからスレッド情報を取得し、パースして返します。
     * 取得またはパースに失敗した場合はnullを返すことを検討できます。
     * (現在の実装ではparseDatがnullを許容しないため、呼び出し側で !! を使っていますが、
     * エラーハンドリングをより丁寧にする場合は変更も考慮)
     */
    suspend fun getThread(
        datUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Pair<List<ReplyInfo>, String?>? = withContext(Dispatchers.Default) { // 計算処理なのでDefaultディスパッチャも検討
        val datContent = remoteDataSource.fetchDatString(datUrl, onProgress)
        if (datContent != null) {
            try {
                parseDat(datContent) // DatParser.kt内の関数を直接呼び出し
            } catch (e: Exception) {
                // パースエラー時の処理 (例: ログ出力、nullを返すなど)
                Log.i("DatRepository", "Failed to parse DAT content: ${e.message}")
                null // またはエラーを通知するカスタムResult型など
            }
        } else {
            Log.i("DatRepository", "Failed to fetch DAT content from $datUrl")
            null // データ取得失敗
        }
    }
}
