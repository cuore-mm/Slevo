package com.websarva.wings.android.bbsviewer.data.datasource.remote

interface DatRemoteDataSource {
    /**
     * 指定されたURLからDATファイルのコンテンツをShift_JIS文字列として取得します。
     * 取得に失敗した場合はnullを返します。
     */
    suspend fun fetchDatString(datUrl: String, onProgress: (Float) -> Unit = {}): String?
}
