package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.model.PostReceipt
import okhttp3.Headers

/**
 * 投稿成功HTTP応答から自分の投稿照合用証拠を抽出するparserの境界。
 *
 * 実装はprovider固有のヘッダー形式を隠し、解析不能な任意値を `null` へ変換する。
 */
interface PostReceiptParser {
    /** 投稿成功応答のヘッダーを照合用の証拠へ変換する。 */
    fun parse(headers: Headers, expectedPostPlace: String? = null): PostReceipt
}
