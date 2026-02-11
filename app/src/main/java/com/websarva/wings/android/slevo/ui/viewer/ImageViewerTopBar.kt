package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction

/**
 * 画像ビューアのトップバーを表示する。
 *
 * 戻る・保存・その他メニューの各アクションを持ち、表示可否は呼び出し元で制御する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageViewerTopBar(
    isVisible: Boolean,
    isMenuExpanded: Boolean,
    imageCount: Int,
    barBackgroundColor: Color,
    barExitDurationMillis: Int,
    onNavigateUp: () -> Unit,
    onSaveClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onMenuActionClick: (ImageMenuAction) -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(barExitDurationMillis)),
    ) {
        TopAppBar(
            title = {},
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(R.string.save),
                        tint = Color.White,
                    )
                }
                Box {
                    IconButton(onClick = onMoreClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = onDismissMenu,
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_add_ng)) },
                            onClick = { onMenuActionClick(ImageMenuAction.ADD_NG) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_copy_image)) },
                            onClick = { onMenuActionClick(ImageMenuAction.COPY_IMAGE) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_copy_image_url)) },
                            onClick = { onMenuActionClick(ImageMenuAction.COPY_IMAGE_URL) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_open_in_other_app)) },
                            onClick = { onMenuActionClick(ImageMenuAction.OPEN_IN_OTHER_APP) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_search_web)) },
                            onClick = { onMenuActionClick(ImageMenuAction.SEARCH_WEB) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_share_image)) },
                            onClick = { onMenuActionClick(ImageMenuAction.SHARE_IMAGE) },
                        )
                        if (imageCount >= 2) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(
                                            R.string.image_menu_save_all_images_with_count,
                                            imageCount,
                                        )
                                    )
                                },
                                onClick = { onMenuActionClick(ImageMenuAction.SAVE_ALL_IMAGES) },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = barBackgroundColor
            ),
            windowInsets = WindowInsets(0)
        )
    }
}
