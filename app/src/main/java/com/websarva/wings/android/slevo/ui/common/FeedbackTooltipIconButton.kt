package com.websarva.wings.android.slevo.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

/**
 * 押下フィードバックと長押しツールチップを備えた共通アイコンボタンを表示する。
 *
 * 長押しでツールチップを表示し、押下中は縮小表示でフィードバックする。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedbackTooltipIconButton(
    modifier: Modifier = Modifier,
    tooltipText: String,
    showTooltipHost: Boolean = true,
    tooltipAnchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Above,
    hazeState: HazeState? = null,
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
        label = "iconButtonPressScale",
    )

    // --- Tooltip state ---
    // Guard: ツールチップ表示不可時は常に閉じる。
    LaunchedEffect(showTooltipHost) {
        if (!showTooltipHost) {
            tooltipState.dismiss()
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            tooltipAnchorPosition
        ),
        tooltip = {
            val tooltipShape = MaterialTheme.shapes.largeIncreased
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 1.dp,
                        shape = tooltipShape,
                        clip = false,
                    )
            ) {
                val surfaceColor = if (hazeState != null) {
                    MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.surfaceBright
                }
                Surface(
                    modifier = Modifier
                        .clip(tooltipShape)
                        .let { baseModifier ->
                            if (hazeState != null) {
                                baseModifier.hazeEffect(state = hazeState)
                            } else {
                                baseModifier
                            }
                        },
                    shape = tooltipShape,
                    color = surfaceColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        text = tooltipText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        state = tooltipState,
        enableUserInput = false,
    ) {
        // --- Button ---
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
