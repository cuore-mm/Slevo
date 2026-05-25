package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.shadow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenuPositionProvider
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * タブ専用のアンカードアクションメニュー。
 *
 * 長押ししたタブ位置に紐づき、「詳細」「固定切替」「閉じる」を表示する。
 * 「タブを閉じる」は破壊的操作として赤字で表示する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnchoredTabActionMenu(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect?,
    hazeState: HazeState?,
    isPinned: Boolean,
    onDismissRequest: () -> Unit,
    onDetailClick: () -> Unit,
    onPinClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    // --- Visibility state ---
    val visibleState = remember {
        MutableTransitionState(false)
    }
    visibleState.targetState = expanded

    // Guard: 非表示完了時、またはアンカー未確定時は描画しない。
    if ((!visibleState.currentState && !visibleState.targetState) || anchorBoundsInWindow == null) {
        return
    }

    // --- Position setup ---
    val positionProvider = remember(anchorBoundsInWindow) {
        AnchoredOverlayMenuPositionProvider(
            anchorBoundsInWindow = anchorBoundsInWindow,
            horizontalAlignment = com.websarva.wings.android.slevo.ui.common.HorizontalAnchorAlignment.Center,
            offsetPx = androidx.compose.ui.unit.IntOffset.Zero,
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val menuShape = MaterialTheme.shapes.largeIncreased
        val menuColor = if (hazeState != null) {
            MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.80f)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        }

        // --- Animated content ---
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 140)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 180),
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                    scaleOut(
                        animationSpec = tween(durationMillis = 140),
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ),
        ) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = menuShape,
                        clip = false,
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .clip(menuShape)
                        .let { baseModifier ->
                            if (hazeState != null) {
                                baseModifier.hazeEffect(state = hazeState)
                            } else {
                                baseModifier
                            }
                        },
                    shape = menuShape,
                    color = menuColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                ) {
                    Column {
                        TabActionMenuItem(
                            text = "詳細",
                            onClick = onDetailClick,
                        )
                        TabActionMenuItem(
                            text = if (isPinned) "タブの固定を解除" else "タブを固定",
                            onClick = onPinClick,
                        )
                        TabActionMenuItem(
                            text = "タブを閉じる",
                            isDestructive = true,
                            onClick = onCloseClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * タブアクションメニュー向けの単一行メニュー項目。
 *
 * [isDestructive] が true の場合、文字色をエラー色で表示する。
 */
@Composable
private fun TabActionMenuItem(
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val textScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "tabActionMenuItemTextScale",
    )

    val textColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = textScale
                scaleY = textScale
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
    )
}

@Preview(showSystemUi = true, showBackground = false)
@Composable
private fun AnchoredTabActionMenuPreview() {
    SlevoTheme {
        AnchoredTabActionMenu(
            expanded = true,
            anchorBoundsInWindow = IntRect(320, 80, 368, 128),
            hazeState = null,
            isPinned = false,
            onDismissRequest = {},
            onDetailClick = {},
            onPinClick = {},
            onCloseClick = {},
        )
    }
}

@Preview(showSystemUi = true, showBackground = false)
@Composable
private fun AnchoredTabActionMenuPinnedPreview() {
    SlevoTheme {
        AnchoredTabActionMenu(
            expanded = true,
            anchorBoundsInWindow = IntRect(320, 80, 368, 128),
            hazeState = null,
            isPinned = true,
            onDismissRequest = {},
            onDetailClick = {},
            onPinClick = {},
            onCloseClick = {},
        )
    }
}
