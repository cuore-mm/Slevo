package com.websarva.wings.android.bbsviewer.data.model

import com.squareup.moshi.Json

data class ImgBbResponse(
    val data: ImgBbData
)

data class ImgBbData(
    @Json(name = "url") val url: String
)
