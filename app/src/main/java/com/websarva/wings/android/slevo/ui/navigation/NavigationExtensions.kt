package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.websarva.wings.android.slevo.ui.util.BoardUrlNormalizationInput
import com.websarva.wings.android.slevo.ui.util.normalizeBoardUrlTo5chIo
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel

/**
 * 板画面への遷移を共通化した拡張関数。
 */
fun NavHostController.navigateToBoard(
    route: AppRoute.Board,
    tabsViewModel: TabsViewModel? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    val normalizedRoute = route.normalizeBoardRoute(tabsViewModel)
    tabsViewModel?.let { viewModel ->
        normalizedRoute.boardId?.takeIf { it != 0L }?.let {
            // 既存板の場合のみ選択状態を更新する（無効URLは検証後に保存）。
            viewModel.ensureBoardTab(normalizedRoute).let { index ->
                if (index >= 0) {
                    viewModel.setBoardCurrentPage(index)
                }
            }
        }
    }
    navigate(normalizedRoute) {
        launchSingleTop = true
        builder()
    }
}

/**
 * スレ画面への遷移を共通化した拡張関数。
 */
fun NavHostController.navigateToThread(
    route: AppRoute.Thread,
    tabsViewModel: TabsViewModel? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    val normalizedRoute = route.normalizeThreadRoute(tabsViewModel)
    tabsViewModel?.let { viewModel ->
        // boardId 未解決でもタブを登録し、選択状態を更新する。
        viewModel.ensureThreadTab(normalizedRoute).let { index ->
            if (index >= 0) {
                viewModel.setThreadCurrentPage(index)
            }
        }
    }
    navigate(normalizedRoute) {
        launchSingleTop = true
        builder()
    }
}

/**
 * 設定に従って 5ch.net の板URLを 5ch.io へ正規化する。
 */
private fun normalizeBoardUrl(
    boardUrl: String,
    tabsViewModel: TabsViewModel?,
): String {
    val isEnabled = tabsViewModel?.isRedirect5chNetToIoEnabled() ?: false
    return normalizeBoardUrlTo5chIo(
        BoardUrlNormalizationInput(
            boardUrl = boardUrl,
            isEnabled = isEnabled,
        )
    )
}

/**
 * 板ルートの boardUrl を正規化して返す。
 */
private fun AppRoute.Board.normalizeBoardRoute(
    tabsViewModel: TabsViewModel?,
): AppRoute.Board {
    val normalizedUrl = normalizeBoardUrl(boardUrl, tabsViewModel)
    if (normalizedUrl == boardUrl) return this
    return copy(boardUrl = normalizedUrl)
}

/**
 * スレルートの boardUrl を正規化して返す。
 *
 * `threadTitle` は表示用名称のため、URL正規化対象には含めない。
 */
private fun AppRoute.Thread.normalizeThreadRoute(
    tabsViewModel: TabsViewModel?,
): AppRoute.Thread {
    val normalizedUrl = normalizeBoardUrl(boardUrl, tabsViewModel)
    if (normalizedUrl == boardUrl) return this
    return copy(boardUrl = normalizedUrl)
}
