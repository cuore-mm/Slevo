package com.websarva.wings.android.slevo.data.model

/**
 * 投稿成功応答から取得した、自分の投稿照合用の証拠を表す。
 *
 * すべての証拠は任意であり、欠落または解析不能な値は `null` として通常の本文照合へ
 * fallbackする。個人情報を含む未使用ヘッダーはこのモデルへ格納しない。
 */
data class PostReceipt(
    val confirmedResNum: Int? = null,
    val serverPostDateMillis: Long? = null,
    val posterIdHint: String? = null,
)
