package com.websarva.wings.android.bbsviewer.data.repository

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class BbsMenuContent(
    val categoryName: String,
    val boards: List<BoardInfo>
)

@Singleton
class BbsMenuRepository @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun fetchBbsMenu(menuUrl: String): List<BbsMenuContent>? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(menuUrl)
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val json = response.body?.string() ?: return@withContext null
                // 以下は既存ロジックのまま
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(BbsMenuResponse::class.java)
                val menu = adapter.fromJson(json) ?: return@withContext null

                menu.menuList.map { category ->
                    BbsMenuContent(
                        categoryName   = category.categoryName,
                        boards = category.categoryContent.map {
                            BoardInfo(it.boardName, it.url)
                        }
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    data class BbsMenuResponse(
        @Json(name = "menu_list") val menuList: List<CategoryData>
    )

    data class CategoryData(
        @Json(name = "category_name") val categoryName: String,
        @Json(name = "category_content") val categoryContent: List<BoardData>
    )

    data class BoardData(
        @Json(name = "board_name") val boardName: String,
        @Json(name = "url") val url: String
    )
}
