package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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
    val backTooltipState = rememberTooltipState(isPersistent = true)
    val saveTooltipState = rememberTooltipState(isPersistent = true)
    val moreTooltipState = rememberTooltipState(isPersistent = true)

    LaunchedEffect(isVisible, isMenuExpanded) {
        if (!isVisible || isMenuExpanded) {
            dismissTooltipStates(backTooltipState, saveTooltipState, moreTooltipState)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(barExitDurationMillis)),
    ) {
        TopAppBar(
            title = {},
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            navigationIcon = {
                FeedbackTooltipIconButton(
                    contentDescription = stringResource(R.string.back),
                    tooltipText = stringResource(R.string.back),
                    tooltipState = backTooltipState,
                    onClick = onNavigateUp,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            },
            actions = {
                FeedbackTooltipIconButton(
                    contentDescription = stringResource(R.string.save),
                    tooltipText = stringResource(R.string.save),
                    tooltipState = saveTooltipState,
                    onClick = onSaveClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Box {
                    FeedbackTooltipIconButton(
                        contentDescription = stringResource(R.string.more),
                        tooltipText = stringResource(R.string.more),
                        tooltipState = moreTooltipState,
                        onClick = onMoreClick,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
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

/**
 * 画像ビューア用のフィードバック付きアイコンボタンを表示する。
 *
 * 押下状態で縮小アニメーションを行い、長押しでプレーンツールチップを表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackTooltipIconButton(
    contentDescription: String,
    tooltipText: String,
    tooltipState: TooltipState,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "topBarIconPressScale",
    )

    PlainTooltipBox(
        tooltip = { PlainTooltip { Text(text = tooltipText) } },
        tooltipState = tooltipState,
    ) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        ) {
            icon()
        }
    }
}

/**
 * 複数ツールチップの表示状態をまとめて閉じる。
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun dismissTooltipStates(vararg tooltipStates: TooltipState) {
    tooltipStates.forEach { tooltipState ->
        if (tooltipState.isVisible) {
            tooltipState.dismiss()
        }
    }
}
