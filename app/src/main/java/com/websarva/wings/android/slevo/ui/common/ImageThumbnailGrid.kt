package com.websarva.wings.android.slevo.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry

/**
 * 画像URL一覧をサムネイルのグリッドとして表示する。
 *
 * タップと長押しを分岐して通知し、サムネイルには共有トランジション用の要素を付与する。
 *
 * タップ時は対象URLと同一投稿内の画像URL一覧およびタップ位置を通知し、長押し時はURL一覧を通知する。
 * 読み込み開始/成功/失敗はコールバック経由で上位に伝播する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImageThumbnailGrid(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    transitionNamespace: String,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: ((String, List<String>) -> Unit)? = null,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    onImageLoadStart: (String) -> Unit = {},
    onImageLoadError: (String, ImageLoadFailureType) -> Unit = { _, _ -> },
    onImageLoadSuccess: (String) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
    enableSharedElement: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val loadProgressByUrl by ImageLoadProgressRegistry.progressByUrl.collectAsState()

    // --- Grid layout ---
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUrls.chunked(3).forEachIndexed { rowIndex, rowItems ->
            val baseIndex = rowIndex * 3
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEachIndexed { columnIndex, url ->
                    val imageIndex = baseIndex + columnIndex
                    ImageThumbnail(
                        url = url,
                        imageUrls = imageUrls,
                        imageIndex = imageIndex,
                        transitionNamespace = transitionNamespace,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onImageClick = onImageClick,
                        onImageLongPress = onImageLongPress,
                        imageLoadFailureByUrl = imageLoadFailureByUrl,
                        loadProgressByUrl = loadProgressByUrl,
                        onImageLoadStart = onImageLoadStart,
                        onImageLoadError = onImageLoadError,
                        onImageLoadSuccess = onImageLoadSuccess,
                        onImageRetry = onImageRetry,
                        enableSharedElement = enableSharedElement,
                        imageContentDescription = stringResource(R.string.image_thumbnail_open),
                        cacheKeyPrefix = "grid",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
