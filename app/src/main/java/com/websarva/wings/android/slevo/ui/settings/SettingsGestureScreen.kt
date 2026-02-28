package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenu
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenuItem
import com.websarva.wings.android.slevo.ui.common.ConfirmBottomDialog
import com.websarva.wings.android.slevo.ui.common.FeedbackTooltipIconButton
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import kotlin.math.roundToInt

/**
 * ジェスチャー設定画面の状態管理と UI を接続する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGestureScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsGestureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsGestureScreenContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        toggleGesture = { viewModel.toggleGesture(it) },
        toggleShowActionHints = { viewModel.toggleGestureShowActionHints(it) },
        onGestureItemClick = { viewModel.onGestureItemClick(it) },
        dismissGestureDialog = { viewModel.dismissGestureDialog() },
        assignGestureAction = { direction, action ->
            viewModel.assignGestureAction(
                direction,
                action
            )
        },
        resetGestureSettings = { viewModel.resetGestureSettings() },
    )
}

/**
 * ジェスチャー設定画面のコンテンツを描画する。
 *
 * 画面内の各カードやメニュー表示をまとめて構成する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGestureScreenContent(
    uiState: SettingsGestureUiState,
    onNavigateUp: () -> Unit,
    toggleGesture: (Boolean) -> Unit,
    toggleShowActionHints: (Boolean) -> Unit,
    onGestureItemClick: (GestureDirection) -> Unit,
    dismissGestureDialog: () -> Unit,
    assignGestureAction: (GestureDirection, GestureAction?) -> Unit,
    resetGestureSettings: () -> Unit,
) {
    // --- State ---
    val haptic = LocalHapticFeedback.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    val closeMenu = { isMenuExpanded = false }

    // --- Layout ---
    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(id = R.string.gesture_settings),
                onNavigateUp = onNavigateUp,
                actions = {
                    Box {
                        FeedbackTooltipIconButton(
                            tooltipText = stringResource(id = R.string.other_options),
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val rect = coordinates.boundsInWindow()
                                menuAnchorBounds = IntRect(
                                    left = rect.left.roundToInt(),
                                    top = rect.top.roundToInt(),
                                    right = rect.right.roundToInt(),
                                    bottom = rect.bottom.roundToInt(),
                                )
                            },
                            onClick = { isMenuExpanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(id = R.string.other_options)
                            )
                        }
                        AnchoredOverlayMenu(
                            expanded = isMenuExpanded,
                            anchorBoundsInWindow = menuAnchorBounds,
                            hazeState = null,
                            onDismissRequest = closeMenu,
                        ) {
                            AnchoredOverlayMenuItem(
                                text = stringResource(id = R.string.gesture_reset_settings),
                                onClick = {
                                    closeMenu()
                                    showResetDialog = true
                                },
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        // --- Gesture groups ---
        val rightDirections = setOf(
            GestureDirection.Right,
            GestureDirection.RightUp,
            GestureDirection.RightLeft,
            GestureDirection.RightDown,
        )
        val leftDirections = setOf(
            GestureDirection.Left,
            GestureDirection.LeftUp,
            GestureDirection.LeftRight,
            GestureDirection.LeftDown,
        )

        val rightGestureItems = uiState.gestureItems.filter { it.direction in rightDirections }
        val leftGestureItems = uiState.gestureItems.filter { it.direction in leftDirections }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                SettingsCardWithListItems(
                    items = listOf(
                        listItemSpecOfBasic(
                            headlineText =
                                if (uiState.isGestureEnabled) stringResource(id = R.string.switch_on)
                                else stringResource(id = R.string.switch_off),
                            headlineStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isGestureEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            ),
                            switchSpec = SwitchSpec(
                                checked = uiState.isGestureEnabled,
                                onCheckedChange = { toggleGesture(it) },
                                enabled = true, // ジェスチャー設定全体の有効/無効に関わらず、常に切り替え可能にしておく
                            ),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggleGesture(!uiState.isGestureEnabled)
                            },
                        )
                    ),
                    // このカード自体は常にクリック可能にしておく（ジェスチャーの有効化/無効化用）
                    cardEnabled = true,
                )
            }
            item {
                Text(
                    text = stringResource(id = R.string.gesture_supporting_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            if (rightGestureItems.isNotEmpty()) {
                item {
                    GestureDirectionGroupCard(
                        gestureItems = rightGestureItems,
                        isGestureEnabled = uiState.isGestureEnabled,
                        onGestureItemClick = onGestureItemClick,
                    )
                }
            }
            if (leftGestureItems.isNotEmpty()) {
                item {
                    GestureDirectionGroupCard(
                        gestureItems = leftGestureItems,
                        isGestureEnabled = uiState.isGestureEnabled,
                        onGestureItemClick = onGestureItemClick,
                    )
                }
            }
            item {
                SettingsCardWithListItems(
                    items = listOf(
                        listItemSpecOfBasic(
                            headlineText = stringResource(id = R.string.gesture_show_action_hint),
                            switchSpec = SwitchSpec(
                                checked = uiState.showActionHints,
                                onCheckedChange = { toggleShowActionHints(it) },
                                enabled = uiState.isGestureEnabled,
                            ),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggleShowActionHints(!uiState.showActionHints)
                            }
                        )
                    ),
                    cardEnabled = uiState.isGestureEnabled,
                )
            }
        }
    }

    // Guard: 選択中ジェスチャーがある場合のみダイアログを表示する。
    uiState.selectedDirection?.let { direction ->
        val currentAction = uiState.gestureItems.firstOrNull { it.direction == direction }?.action
        val actions = GestureAction.entries.toList()
        GestureActionDialog(
            direction = direction,
            currentAction = currentAction,
            actions = actions,
            onDismissRequest = { dismissGestureDialog() },
            onActionSelected = { action -> assignGestureAction(direction, action) }
        )
    }

    if (showResetDialog) {
        ResetGestureSettingsDialog(
            onDismissRequest = { showResetDialog = false },
            onConfirm = {
                resetGestureSettings()
                showResetDialog = false
            }
        )
    }
}

/**
 * ジェスチャー設定の初期化確認ダイアログを表示する。
 */
@Composable
private fun ResetGestureSettingsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmBottomDialog(
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
        titleText = stringResource(id = R.string.gesture_reset_settings),
        messageText = stringResource(id = R.string.gesture_reset_settings_description),
        confirmLabel = stringResource(id = R.string.reset),
        cancelLabel = stringResource(id = R.string.cancel),
        confirmEnabled = true
    )
}

/**
 * 方向別のジェスチャー項目をカードとして表示する。
 */
@Composable
private fun GestureDirectionGroupCard(
    gestureItems: List<GestureItem>,
    isGestureEnabled: Boolean,
    onGestureItemClick: (GestureDirection) -> Unit,
) {
    val specs = gestureItems.map { item ->
        val directionLabel = stringResource(id = item.direction.labelRes)
        val actionLabel = item.action?.let { stringResource(id = it.labelRes) }
            ?: stringResource(id = R.string.gesture_action_unassigned)

        listItemSpecOfBasic(
            leadingContent = {
                Icon(
                    painter = painterResource(id = item.direction.iconRes),
                    contentDescription = directionLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            headlineText = directionLabel,
            supportingText = actionLabel,
            supportingStyle = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Normal,
            ),
            onClick = if (isGestureEnabled) {
                { onGestureItemClick(item.direction) }
            } else null,
        )
    }

    SettingsCardWithListItems(items = specs, cardEnabled = isGestureEnabled)
}

@Preview(showBackground = true)
@Composable
private fun SettingsGestureScreenPreview() {
    val sampleState = SettingsGestureUiState(
        isGestureEnabled = true,
        gestureItems = GestureDirection.entries.map { direction ->
            GestureItem(direction = direction, action = GestureAction.entries.firstOrNull())
        },
//        selectedDirection = GestureDirection.entries.first(),
    )

    MaterialTheme {
        SettingsGestureScreenContent(
            uiState = sampleState,
            onNavigateUp = {},
            toggleGesture = {},
            toggleShowActionHints = {},
            onGestureItemClick = {},
            dismissGestureDialog = {},
            assignGestureAction = { _, _ -> },
            resetGestureSettings = {},
        )
    }
}
