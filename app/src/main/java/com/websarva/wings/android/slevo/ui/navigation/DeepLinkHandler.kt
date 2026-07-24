package com.websarva.wings.android.slevo.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.resolveDeepLinkUrl
import kotlinx.coroutines.CancellationException

/**
 * Deep Linkを受け取り、板/スレ画面へ遷移する。
 */
@Composable
fun DeepLinkHandler(
    deepLinkUrl: String?,
    navController: NavHostController,
    tabSessionStore: TabSessionStore,
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
                tabSessionStore = tabSessionStore
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
    tabSessionStore: TabSessionStore
): Boolean {
    // --- Target resolution ---
    val target = resolveDeepLinkUrl(url) ?: return false // 対象外URLは処理しない。

    // --- Navigation ---
    return when (target) {
        is ResolvedUrl.ItestBoard -> {
            val host = tabSessionStore.resolveBoardHost(
                boardKey = target.boardKey,
                sourceUrl = target.rawUrl,
            ) ?: return false
            val boardUrl = "https://$host/${target.boardKey}/"
            val route = tabSessionStore.normalizeBoardRouteForNavigation(
                AppRoute.Board(
                    boardName = boardUrl,
                    boardUrl = boardUrl
                )
            )
            tabSessionStore.registerAndSelectBoardRoute(route)
            navController.navigateToBoardScreen(route)
            true
        }
        is ResolvedUrl.Thread -> {
            val boardUrl = "https://${target.host}/${target.boardKey}/"
            val route = tabSessionStore.normalizeThreadRouteForNavigation(
                AppRoute.Thread(
                    threadKey = target.threadKey,
                    boardUrl = boardUrl,
                    boardName = target.boardKey,
                    threadTitle = null
                )
            )
            try {
                handleThreadDeepLinkRoute(
                    route = route,
                    tabSessionStore = tabSessionStore,
                    navigate = { navController.navigateToThreadScreen(route) },
                )
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (_: Throwable) {
                false
            }
        }
        is ResolvedUrl.Board -> {
            val boardUrl = "https://${target.host}/${target.boardKey}/"
            val route = tabSessionStore.normalizeBoardRouteForNavigation(
                AppRoute.Board(
                    boardName = boardUrl,
                    boardUrl = boardUrl
                )
            )
            tabSessionStore.registerAndSelectBoardRoute(route)
            navController.navigateToBoardScreen(route)
            true
        }
        is ResolvedUrl.Unknown -> false
    }
}

/**
 * Completes thread deep-link registration before changing selection or navigation.
 * The callback is invoked only after readiness, canonical existence, and selection succeed.
 */
internal suspend fun handleThreadDeepLinkRoute(
    route: AppRoute.Thread,
    tabSessionStore: TabSessionStore,
    navigate: () -> Unit,
): Boolean {
    // --- Readiness and registration ---
    tabSessionStore.awaitThreadTabsReady()
    val threadId = parseBoardUrl(route.boardUrl)?.let { (host, board) ->
        ThreadId.of(host, board, route.threadKey)
    } ?: return false
    val registrationIndex = tabSessionStore.registerThreadRoute(route)
    if (registrationIndex < 0 || !tabSessionStore.isCanonicalThreadTab(threadId)) return false

    // --- Selection and navigation ---
    if (!tabSessionStore.selectThreadTab(threadId)) return false
    navigate()
    return true
}
