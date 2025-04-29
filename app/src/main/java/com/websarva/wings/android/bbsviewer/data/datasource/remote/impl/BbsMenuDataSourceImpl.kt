package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BbsMenuDataSource
import com.websarva.wings.android.bbsviewer.data.model.BbsMenuContent
import com.websarva.wings.android.bbsviewer.data.model.Board
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BBS メニューを取得する DataSource の実装。
 * 指定された URL から取得したレスポンスを JSON か HTML か判定し、
 * それぞれ適切にパースしてカテゴリとボード一覧を返却します。
 */
@Singleton
class BbsMenuDataSourceImpl @Inject constructor(
    private val client: OkHttpClient
) : BbsMenuDataSource {

    companion object {
        private const val TAG = "BbsMenuDataSourceImpl"
    }

    /**
     * 指定された menuUrl から BBS メニューを取得し、
     * JSON あるいは HTML としてパースした結果を返却します。
     *
     * @param menuUrl BBS メニューのエンドポイント URL
     * @return パースに成功した場合はカテゴリごとのメニューリスト、失敗時は null
     */
    override suspend fun fetchBbsMenu(menuUrl: String): List<BbsMenuContent>? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(menuUrl)
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                return@withContext parseMenuResponse(body)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch menu from $menuUrl", e)
                null
            }
        }

    /**
     * レスポンス文字列の先頭を判定し、
     * '<' で始まる場合は HTML、それ以外は JSON としてパースします。
     *
     * @param body レスポンスの文字列コンテンツ
     * @return パース結果のメニューリスト、失敗時は null
     */
    private fun parseMenuResponse(body: String): List<BbsMenuContent>? {
        return if (body.trimStart().startsWith("<")) {
            parseMenuHtml(body)
        } else {
            parseMenuJson(body)
        }
    }

    /**
     * JSON 形式のメニュー文字列を Moshi でパースします。
     *
     * @param json JSON 文字列
     * @return BbsMenuContent のリスト、パース失敗時は null
     */
    private fun parseMenuJson(json: String): List<BbsMenuContent>? {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(BbsMenuResponse::class.java)
        val menu = adapter.fromJson(json) ?: return null

        return menu.menuList.map { category ->
            BbsMenuContent(
                categoryName = category.categoryName,
                boards = category.categoryContent.map { Board(it.boardName, it.url) }
            )
        }
    }

    /**
     * HTML 形式の bbsmenu を Jsoup でパースします。
     * <font> 内の <B> タグでカテゴリ名を取得し、
     * その次の <A> タグ群を該当カテゴリのボードとして収集します。
     *
     * @param html HTML 文字列
     * @return BbsMenuContent のリスト、パース失敗時は null
     */
    private fun parseMenuHtml(html: String): List<BbsMenuContent>? {
        try {
            val doc = Jsoup.parse(html)
            // 画面構造の多くは <font> 要素内にカテゴリ・リンクが配置されている
            val container = doc.selectFirst("font") ?: doc.body()
            val elements = container.children()
            val contents = mutableListOf<BbsMenuContent>()

            var i = 0
            while (i < elements.size) {
                val el = elements[i]
                if (el.tagName().equals("b", ignoreCase = true)) {
                    val categoryName = el.text().trim()
                    if (categoryName.isNotEmpty()) {
                        val boards = mutableListOf<Board>()
                        var j = i + 1
                        while (j < elements.size) {
                            val sib = elements[j]
                            if (sib.tagName().equals("b", ignoreCase = true)) break
                            if (sib.tagName().equals("a", ignoreCase = true)) {
                                val name = sib.text().trim()
                                val href = sib.absUrl("href").ifEmpty { sib.attr("href") }
                                boards.add(Board(name, href))
                            }
                            j++
                        }
                        contents.add(BbsMenuContent(categoryName = categoryName, boards = boards))
                        i = j - 1
                    }
                }
                i++
            }

            return contents.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse bbsmenu HTML", e)
            return null
        }
    }

    // JSON パース用の内部 DTO 定義
    private data class BbsMenuResponse(
        @Json(name = "menu_list") val menuList: List<CategoryData>
    )

    private data class CategoryData(
        @Json(name = "category_name") val categoryName: String,
        @Json(name = "category_content") val categoryContent: List<BoardData>
    )

    private data class BoardData(
        @Json(name = "board_name") val boardName: String,
        @Json(name = "url") val url: String
    )
}
