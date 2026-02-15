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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenu
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenuDriver
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenuItem
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    foregroundColor: Color,
    tooltipBackgroundColor: Color,
    barExitDurationMillis: Int,
    onNavigateUp: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onMenuActionClick: (ImageMenuAction) -> Unit,
) {
    var menuAnchorBounds by remember { mutableStateOf<IntRect?>(null) }

    // --- Visibility ---
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
                    foregroundColor = foregroundColor,
                    tooltipBackgroundColor = tooltipBackgroundColor,
                    onClick = onNavigateUp,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(R.string.back),
                        tint = foregroundColor,
                    )
                }
            },
            actions = {
                FeedbackTooltipIconButton(
                    tooltipText = stringResource(R.string.save),
                    showTooltipHost = isVisible && !isMenuExpanded,
                    foregroundColor = foregroundColor,
                    tooltipBackgroundColor = tooltipBackgroundColor,
                    onClick = onSaveClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.save),
                        tint = foregroundColor,
                    )
                }
                FeedbackTooltipIconButton(
                    tooltipText = stringResource(R.string.share),
                    showTooltipHost = isVisible && !isMenuExpanded,
                    foregroundColor = foregroundColor,
                    tooltipBackgroundColor = tooltipBackgroundColor,
                    onClick = onShareClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.share),
                        tint = foregroundColor,
                    )
                }
                Box {
                    FeedbackTooltipIconButton(
                        tooltipText = stringResource(R.string.other_options),
                        showTooltipHost = isVisible && !isMenuExpanded,
                        foregroundColor = foregroundColor,
                        tooltipBackgroundColor = tooltipBackgroundColor,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val rect = coordinates.boundsInWindow()
                            menuAnchorBounds = IntRect(
                                left = rect.left.roundToInt(),
                                top = rect.top.roundToInt(),
                                right = rect.right.roundToInt(),
                                bottom = rect.bottom.roundToInt(),
                            )
                        },
                        onClick = onMoreClick,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.other_options),
                            tint = foregroundColor,
                        )
                    }
                    AnchoredOverlayMenu(
                        expanded = isMenuExpanded,
                        anchorBoundsInWindow = menuAnchorBounds,
                        onDismissRequest = onDismissMenu,
                    ) {
                        AnchoredOverlayMenuItem(
                            text = stringResource(R.string.image_menu_add_ng),
                            onClick = { onMenuActionClick(ImageMenuAction.ADD_NG) },
                        )
                        AnchoredOverlayMenuDriver()
                        AnchoredOverlayMenuItem(
                            text = stringResource(R.string.image_menu_copy_image),
                            onClick = { onMenuActionClick(ImageMenuAction.COPY_IMAGE) },
                        )
                        AnchoredOverlayMenuItem(
                            text = stringResource(R.string.image_menu_copy_image_url),
                            onClick = { onMenuActionClick(ImageMenuAction.COPY_IMAGE_URL) },
                        )
                        AnchoredOverlayMenuDriver()
                        AnchoredOverlayMenuItem(
                            text = stringResource(R.string.image_menu_open_in_other_app),
                            onClick = { onMenuActionClick(ImageMenuAction.OPEN_IN_OTHER_APP) },
                        )
                        AnchoredOverlayMenuItem(
                            text = stringResource(R.string.image_menu_search_web),
                            onClick = { onMenuActionClick(ImageMenuAction.SEARCH_WEB) },
                        )
                        AnchoredOverlayMenuDriver()
                        if (imageCount >= 2) {
                            AnchoredOverlayMenuItem(
                                text = stringResource(
                                    R.string.image_menu_save_all_images_short,
                                    imageCount,
                                ),
                                onClick = { onMenuActionClick(ImageMenuAction.SAVE_ALL_IMAGES) },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = barBackgroundColor,
                navigationIconContentColor = foregroundColor,
                actionIconContentColor = foregroundColor,
                titleContentColor = foregroundColor,
            ),
            windowInsets = WindowInsets(0),
        )
    }
}

/**
 * 画像ビューア用のフィードバック付きアイコンボタンを表示する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedbackTooltipIconButton(
    tooltipText: String,
    showTooltipHost: Boolean,
    foregroundColor: Color,
    tooltipBackgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "topBarIconPressScale",
    )

    // Guard: バー非表示時やメニュー表示中はツールチップを閉じる。
    LaunchedEffect(showTooltipHost) {
        if (!showTooltipHost) {
            tooltipState.dismiss()
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below,
        ),
        tooltip = {
            PlainTooltip(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.largeIncreased,
                containerColor = tooltipBackgroundColor,
                contentColor = foregroundColor,
                shadowElevation = 1.dp,
                tonalElevation = 1.dp,
            ) {
                Text(
                    text = tooltipText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = foregroundColor,
                )
            }
        },
        state = tooltipState,
        enableUserInput = false,
    ) {
        Box(
            modifier = modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(color = Color.Transparent, shape = CircleShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = {
                        coroutineScope.launch { tooltipState.dismiss() }
                        onClick()
                    },
                    onLongClick = {
                        if (showTooltipHost) {
                            coroutineScope.launch { tooltipState.show() }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Preview
@Composable
private fun ImageViewerTopBarPreview() {
    SlevoTheme {
        ImageViewerTopBar(
            isVisible = true,
            isMenuExpanded = false,
            imageCount = 3,
            barBackgroundColor = Color.Black.copy(alpha = 0.5f),
            foregroundColor = Color.White,
            tooltipBackgroundColor = Color.Black.copy(alpha = 0.5f),
            barExitDurationMillis = 300,
            onNavigateUp = {},
            onSaveClick = {},
            onShareClick = {},
            onMoreClick = {},
            onDismissMenu = {},
            onMenuActionClick = {}
        )
    }
}

@Preview(showSystemUi = true, showBackground = false)
@Composable
private fun ImageViewerTopBarMenuExpandedPreview() {
    SlevoTheme {
        ImageViewerTopBar(
            isVisible = true,
            isMenuExpanded = true,
            imageCount = 3,
            barBackgroundColor = Color.Black.copy(alpha = 0.5f),
            foregroundColor = Color.White,
            tooltipBackgroundColor = Color.Black.copy(alpha = 0.5f),
            barExitDurationMillis = 300,
            onNavigateUp = {},
            onSaveClick = {},
            onShareClick = {},
            onMoreClick = {},
            onDismissMenu = {},
            onMenuActionClick = {}
        )
    }
}
