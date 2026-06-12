package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore

/**
 * 板画面への遷移を共通化した拡張関数。
 */
fun NavHostController.navigateToBoard(
    route: AppRoute.Board,
    tabSessionStore: TabSessionStore? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    tabSessionStore?.let { store ->
        route.boardId?.takeIf { it != 0L }?.let {
            // 既存板の場合のみ選択状態を更新する（無効URLは検証後に保存）。
            store.ensureBoardTab(route).let { index ->
                if (index >= 0) {
                    store.setBoardCurrentPage(index)
                }
            }
        }
    }
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}

/**
 * スレ画面への遷移を共通化した拡張関数。
 */
fun NavHostController.navigateToThread(
    route: AppRoute.Thread,
    tabSessionStore: TabSessionStore? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    tabSessionStore?.let { store ->
        // boardId 未解決でもタブを登録し、選択状態を更新する。
        store.ensureThreadTab(route).let { index ->
            if (index >= 0) {
                store.setThreadCurrentPage(index)
            }
        }
    }
    navigate(route) {
        launchSingleTop = true
        builder()
    }
}
