package com.websarva.wings.android.bbsviewer.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.Response

interface DatService {
    @GET
    suspend fun fetchDat(@Url url: String): Response<ResponseBody>
}
