package com.websarva.wings.android.bbsviewer.data.datasource.local

interface BoardRemoteDataSource {
    /**
     * subject.txt を取得し、304→null、200→Shift_JIS でデコードした文字列を返す。
     */
    suspend fun fetchSubjectTxt(url: String): String?
}
