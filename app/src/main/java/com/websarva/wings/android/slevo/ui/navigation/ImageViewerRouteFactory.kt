package com.websarva.wings.android.slevo.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 画像ビューア遷移用の route を組み立てる。
 *
 * URL 一覧が空の場合は遷移対象がないため `null` を返す。
 */
fun buildImageViewerRoute(
    imageUrls: List<String>,
    tappedIndex: Int,
    transitionNamespace: String,
): AppRoute.ImageViewer? {
    if (imageUrls.isEmpty()) {
        return null
    }
    val encodedUrls = imageUrls.map { imageUrl ->
        URLEncoder.encode(imageUrl, StandardCharsets.UTF_8.toString())
    }
    val initialIndex = tappedIndex.coerceIn(encodedUrls.indices)
    return AppRoute.ImageViewer(
        imageUrls = encodedUrls,
        initialIndex = initialIndex,
        transitionNamespace = transitionNamespace,
    )
}
