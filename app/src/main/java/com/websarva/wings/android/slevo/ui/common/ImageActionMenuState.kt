package com.websarva.wings.android.slevo.ui.common

import androidx.compose.runtime.Immutable
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType

/**
 * 画像アクションメニューの表示状態。
 *
 * 読み込み状態グループと一括保存件数をまとめて保持する。
 */
@Immutable
data class ImageActionMenuState(
    val group: ImageActionMenuGroup,
    val saveAllImageCount: Int,
)

/**
 * 画像アクションメニューの表示グループ種別。
 */
@Immutable
enum class ImageActionMenuGroup {
    SUCCESS,
    FAIL_404_410,
    FAIL_OTHER,
    LOADING,
}

/**
 * 画像URLの読み込み状態からメニュー表示状態を導出する。
 */
fun resolveImageActionMenuState(
    imageUrl: String,
    imageUrls: List<String>,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    loadingImageUrls: Set<String>,
): ImageActionMenuState {
    if (imageUrl.isBlank()) {
        // Guard: 空URLは成功扱いにして既存メニューを維持する。
        return ImageActionMenuState(
            group = ImageActionMenuGroup.SUCCESS,
            saveAllImageCount = imageUrls.size,
        )
    }
    val group = when {
        imageUrl in loadingImageUrls -> ImageActionMenuGroup.LOADING
        imageLoadFailureByUrl[imageUrl] == ImageLoadFailureType.HTTP_404 ||
            imageLoadFailureByUrl[imageUrl] == ImageLoadFailureType.HTTP_410 -> {
            ImageActionMenuGroup.FAIL_404_410
        }

        imageLoadFailureByUrl.containsKey(imageUrl) -> ImageActionMenuGroup.FAIL_OTHER
        else -> ImageActionMenuGroup.SUCCESS
    }
    return ImageActionMenuState(
        group = group,
        saveAllImageCount = imageUrls.size,
    )
}
