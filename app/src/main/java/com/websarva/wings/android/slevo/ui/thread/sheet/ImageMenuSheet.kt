package com.websarva.wings.android.slevo.ui.thread.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.BottomSheetListItem
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet

/**
 * 画像メニューで扱うアクション種別。
 *
 * ボトムシートで選択された項目を上位へ通知するために利用する。
 */
enum class ImageMenuAction {
    ADD_NG,
    COPY_IMAGE,
    COPY_IMAGE_URL,
    OPEN_IN_OTHER_APP,
    SAVE_ALL_IMAGES,
    SAVE_IMAGE,
    SEARCH_WEB,
    SHARE_IMAGE,
}

/**
 * 画像メニューのボトムシートを表示する。
 *
 * 表示条件を満たす場合のみメニューを描画し、選択アクションを通知する。
 *
 * レス内画像が複数ある場合は一括保存の項目を追加する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMenuSheet(
    show: Boolean,
    imageUrl: String?,
    imageUrls: List<String>,
    onActionSelected: (ImageMenuAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!show || imageUrl.isNullOrBlank()) {
        // 表示条件を満たさない場合は描画しない。
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    SlevoBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        ImageMenuSheetContent(
            onActionSelected = onActionSelected,
            saveAllImageCount = imageUrls.size,
        )
    }
}

/**
 * 画像メニューの項目一覧を表示する。
 *
 * 各項目は選択されたアクションを上位へ渡す。
 */
@Composable
fun ImageMenuSheetContent(
    onActionSelected: (ImageMenuAction) -> Unit,
    saveAllImageCount: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_add_ng),
            icon = Icons.Outlined.Block,
            onClick = { onActionSelected(ImageMenuAction.ADD_NG) }
        )
        HorizontalDivider()
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_save_image),
            icon = Icons.Outlined.Download,
            onClick = { onActionSelected(ImageMenuAction.SAVE_IMAGE) }
        )
        if (saveAllImageCount >= 2) {
            BottomSheetListItem(
                text = stringResource(
                    R.string.image_menu_save_all_images_with_count,
                    saveAllImageCount,
                ),
                leadingContent = { SaveAllImagesIcon() },
                onClick = { onActionSelected(ImageMenuAction.SAVE_ALL_IMAGES) }
            )
        }
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_copy_image),
            icon = Icons.Outlined.ContentCopy,
            onClick = { onActionSelected(ImageMenuAction.COPY_IMAGE) }
        )
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_copy_image_url),
            icon = null,
            onClick = { onActionSelected(ImageMenuAction.COPY_IMAGE_URL) }
        )
        HorizontalDivider()
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_open_in_other_app),
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            onClick = { onActionSelected(ImageMenuAction.OPEN_IN_OTHER_APP) }
        )
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_search_web),
            icon = Icons.Outlined.Search,
            onClick = { onActionSelected(ImageMenuAction.SEARCH_WEB) }
        )
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_share_image),
            icon = Icons.Outlined.Share,
            onClick = { onActionSelected(ImageMenuAction.SHARE_IMAGE) }
        )
    }
}

/**
 * 一括保存メニュー用の合成アイコン。
 *
 * download をベースに、複数対象を示す filter_none バッジを右下へ重ねる。
 */
@Composable
private fun SaveAllImagesIcon() {
    Box(modifier = Modifier.size(24.dp)) {
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.CenterStart)
        )
        Icon(
            imageVector = Icons.Outlined.FilterNone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 1.dp, y = 1.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun ImageMenuSheetPreview() {
    MaterialTheme {
        ImageMenuSheet(
            show = true,
            imageUrl = "https://example.com/image.png",
            imageUrls = listOf(
                "https://example.com/image.png",
                "https://example.com/image2.png",
            ),
            onActionSelected = {},
            onDismissRequest = {},
        )
    }
}
