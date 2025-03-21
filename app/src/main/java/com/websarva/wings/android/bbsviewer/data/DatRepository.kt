package com.websarva.wings.android.bbsviewer.data

import android.util.Log
import com.websarva.wings.android.bbsviewer.network.DatService
import java.nio.charset.Charset
import javax.inject.Inject

class DatRepository @Inject constructor(
    private val api: DatService
) {
    suspend fun fetchDatData(datUrl: String): String? {
        return try {
            val response = api.fetchDat(datUrl)
            if (response.isSuccessful) {
                // 生のバイト列を取得して、Shift_JIS でデコードする
                response.body()?.bytes()?.let { bytes ->
                    String(bytes, Charset.forName("Shift_JIS"))
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

