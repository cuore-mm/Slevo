package com.websarva.wings.android.slevo.data.repository.fake

import com.websarva.wings.android.slevo.data.datasource.remote.BbsMenuDataSource
import com.websarva.wings.android.slevo.data.model.BbsMenuContent

/**
 * テスト用の `BbsMenuDataSource` fake。
 *
 * 期待される menuUrl に対する menu データを保持し、`fetchBbsMenu` で返す。
 */
class FakeBbsMenuDataSource(
    private val menuByUrl: Map<String, List<BbsMenuContent>> = emptyMap(),
    private val defaultResult: List<BbsMenuContent>? = null,
) : BbsMenuDataSource {
    val requestedUrls = mutableListOf<String>()

    override suspend fun fetchBbsMenu(menuUrl: String): List<BbsMenuContent>? {
        requestedUrls += menuUrl
        return menuByUrl[menuUrl] ?: defaultResult
    }
}
