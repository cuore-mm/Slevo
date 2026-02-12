package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import kotlinx.coroutines.delay

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
                FeedbackTooltipIconButton(
                    tooltipText = stringResource(R.string.back),
                    showTooltipHost = isVisible && !isMenuExpanded,
                    onClick = onNavigateUp,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
            },
            actions = {
                FeedbackTooltipIconButton(
                    tooltipText = stringResource(R.string.save),
                    showTooltipHost = isVisible && !isMenuExpanded,
                    onClick = onSaveClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(R.string.save),
                        tint = Color.White,
                    )
                }
                Box {
                    FeedbackTooltipIconButton(
                        tooltipText = stringResource(R.string.more),
                        showTooltipHost = isVisible && !isMenuExpanded,
                        onClick = onMoreClick,
                    ) {
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

/**
 * 画像ビューア用のフィードバック付きアイコンボタンを表示する。
 *
 * 押下状態で縮小アニメーションを行い、長押しでプレーンツールチップを表示する。
 */
@Composable
private fun FeedbackTooltipIconButton(
    tooltipText: String,
    showTooltipHost: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    var isTooltipVisible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "topBarIconPressScale",
    )

    LaunchedEffect(showTooltipHost) {
        if (!showTooltipHost) {
            isTooltipVisible = false
        }
    }

    LaunchedEffect(isTooltipVisible) {
        if (isTooltipVisible) {
            delay(1500)
            isTooltipVisible = false
        }
    }

    Box(contentAlignment = Alignment.Center) {
        if (isTooltipVisible) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(x = 0, y = with(density) { (-44).dp.roundToPx() }),
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = tooltipText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(color = Color.Transparent, shape = CircleShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = {
                        isTooltipVisible = false
                        onClick()
                    },
                    onLongClick = {
                        if (showTooltipHost) {
                            isTooltipVisible = true
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}
