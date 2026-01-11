package com.websarva.wings.android.slevo.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.resolveDeepLinkUrl

/**
 * Deep Linkを受け取り、板/スレ画面へ遷移する。
 */
@Composable
fun DeepLinkHandler(
    deepLinkUrl: String?,
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    onConsumed: () -> Unit
) {
    val context = LocalContext.current
    val invalidUrlMessage = stringResource(R.string.invalid_url)

    LaunchedEffect(deepLinkUrl) {
        if (deepLinkUrl.isNullOrBlank()) {
            return@LaunchedEffect
        }

        try {
            // --- Routing ---
            val handled = handleDeepLinkUrl(
                url = deepLinkUrl,
                navController = navController,
                tabsViewModel = tabsViewModel
            )

            if (!handled) {
                Toast.makeText(context, invalidUrlMessage, Toast.LENGTH_SHORT).show()
            }
        } finally {
            onConsumed()
        }
    }
}

/**
 * Deep LinkのURLを解析して遷移し、成功時にtrueを返す。
 */
private suspend fun handleDeepLinkUrl(
    url: String,
    navController: NavHostController,
    tabsViewModel: TabsViewModel
): Boolean {
    // --- Target resolution ---
    val target = resolveDeepLinkUrl(url) ?: return false // 対象外URLは処理しない。

    // --- Navigation ---
    return when (target) {
        is ResolvedUrl.ItestBoard -> {
            val host = tabsViewModel.resolveBoardHost(target.boardKey) ?: return false
            val boardUrl = "https://$host/${target.boardKey}/"
            val route = AppRoute.Board(
                boardName = boardUrl,
                boardUrl = boardUrl
            )
            navController.navigateToBoard(
                route = route,
                tabsViewModel = tabsViewModel
            )
            true
        }
        is ResolvedUrl.Thread -> {
            val boardUrl = "https://${target.host}/${target.boardKey}/"
            val route = AppRoute.Thread(
                threadKey = target.threadKey,
                boardUrl = boardUrl,
                boardName = target.boardKey,
                threadTitle = url
            )
            navController.navigateToThread(
                route = route,
                tabsViewModel = tabsViewModel
            )
            true
        }
        is ResolvedUrl.Board -> {
            val boardUrl = "https://${target.host}/${target.boardKey}/"
            val route = AppRoute.Board(
                boardName = boardUrl,
                boardUrl = boardUrl
            )
            navController.navigateToBoard(
                route = route,
                tabsViewModel = tabsViewModel
                )
            true
        }
        is ResolvedUrl.Unknown -> false
    }
}
