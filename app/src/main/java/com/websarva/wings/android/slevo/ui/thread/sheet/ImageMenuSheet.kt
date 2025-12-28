package com.websarva.wings.android.slevo.ui.thread.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
    SEARCH_WEB,
    SHARE_IMAGE,
}

/**
 * 画像メニューのボトムシートを表示する。
 *
 * 表示条件を満たす場合のみメニューを描画し、選択アクションを通知する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMenuSheet(
    show: Boolean,
    imageUrl: String?,
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
        ImageMenuSheetContent(onActionSelected = onActionSelected)
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_add_ng),
            icon = Icons.Outlined.Block,
            onClick = { onActionSelected(ImageMenuAction.ADD_NG) }
        )
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_copy_image),
            icon = Icons.Outlined.ContentCopy,
            onClick = { onActionSelected(ImageMenuAction.COPY_IMAGE) }
        )
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_copy_image_url),
            icon = Icons.Outlined.Link,
            onClick = { onActionSelected(ImageMenuAction.COPY_IMAGE_URL) }
        )
        BottomSheetListItem(
            text = stringResource(R.string.image_menu_open_in_other_app),
            icon = Icons.Outlined.OpenInNew,
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun ImageMenuSheetPreview() {
    MaterialTheme {
        ImageMenuSheet(
            show = true,
            imageUrl = "https://example.com/image.png",
            onActionSelected = {},
            onDismissRequest = {},
        )
    }
}
