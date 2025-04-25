package com.websarva.wings.android.bbsviewer.data.datasource.remote

import com.websarva.wings.android.bbsviewer.data.model.BbsMenuContent

/** ネットワークからメニュー JSON/HTML を取得する責務 */
interface BbsMenuDataSource {
    suspend fun fetchBbsMenu(menuUrl: String): List<BbsMenuContent>?
}
