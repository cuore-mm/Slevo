package com.websarva.wings.android.slevo.data.model

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class ImgBbResponse(
    val data: ImgBbData
)

@Keep
@JsonClass(generateAdapter = true)
data class ImgBbData(
    @Json(name = "url") val url: String
)
