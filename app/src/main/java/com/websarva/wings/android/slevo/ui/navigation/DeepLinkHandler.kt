package com.websarva.wings.android.slevo.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.util.DeepLinkTarget
import com.websarva.wings.android.slevo.ui.util.normalizeDeepLinkUrl
import com.websarva.wings.android.slevo.ui.util.parseDeepLinkTarget

/**
 * Handles Deep Links and navigates to board/thread screens.
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
            // --- Normalize ---
            val normalizedUrl = normalizeDeepLinkUrl(deepLinkUrl)

            // --- Routing ---
            val handled = handleDeepLinkUrl(
                url = normalizedUrl,
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
 * Resolves a Deep Link URL into navigation and returns true on success.
 */
private suspend fun handleDeepLinkUrl(
    url: String,
    navController: NavHostController,
    tabsViewModel: TabsViewModel
): Boolean {
    // --- Target resolution ---
    val target = parseDeepLinkTarget(url) ?: return false // 対象外URLは処理しない。

    // --- Navigation ---
    return when (target) {
        is DeepLinkTarget.Itest -> {
            val host = tabsViewModel.resolveBoardHost(target.boardKey) ?: return false
            val boardUrl = "https://$host/${target.boardKey}/"
            if (target.threadKey != null) {
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
            } else {
                val route = AppRoute.Board(
                    boardName = boardUrl,
                    boardUrl = boardUrl
                )
                navController.navigateToBoard(
                    route = route,
                    tabsViewModel = tabsViewModel
                )
            }
            true
        }
        is DeepLinkTarget.Thread -> {
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
        is DeepLinkTarget.Board -> {
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
    }
}
