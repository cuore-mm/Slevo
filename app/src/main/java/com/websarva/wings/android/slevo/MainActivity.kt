package com.websarva.wings.android.slevo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.websarva.wings.android.slevo.ui.AppScaffold
import com.websarva.wings.android.slevo.ui.PendingRestoreResultViewModel
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * アプリのメイン画面を構成し、Deep Linkの受信も受け持つActivity。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val pendingRestoreResultViewModel: PendingRestoreResultViewModel by viewModels()
    @Inject lateinit var tabSessionStore: TabSessionStore
    private val deepLinkUrlState = MutableStateFlow<String?>(null)

    /** window、lifecycle通知再評価、Compose root UIを初期化する。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- Window setup ---
        enableEdgeToEdge()
        // レイアウトをキーボード表示時にリサイズさせる（ime パディングが即座に反映されやすくなる）
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // --- Deep link initialization ---
        updateDeepLinkIntent(intent)

        lifecycleScope.launch {
            observePendingRestoreResultLifecycle(
                lifecycle = lifecycle,
                onStarted = pendingRestoreResultViewModel::startObservation,
                onStopped = pendingRestoreResultViewModel::stopObservation,
            )
        }

        // --- Compose content ---
        setContent {
            val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val pendingRestoreResultUiState by pendingRestoreResultViewModel.uiState
                .collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = uiState.themeMode.resolveDarkTheme(isSystemDark)

            // 2) LocalView を使って Window を取り出し、InsetsController を作成
            val view = LocalView.current
            val window = (view.context as Activity).window
            val insetsController = WindowInsetsControllerCompat(window, view)

            // 3) サイドエフェクトで毎フレーム、ステータスバーのアイコン色を制御
            SideEffect {
                // true にすると「ステータスバー背景が明るい → アイコンをダークに」なる
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            }

            SlevoTheme(darkTheme = isDarkTheme) {
                AppScaffold(
                    settingsViewModel = settingsViewModel,
                    tabSessionStore = tabSessionStore,
                    pendingRestoreResultUiState = pendingRestoreResultUiState,
                    onPendingRestoreResultDisplayed = pendingRestoreResultViewModel::acknowledgeResult,
                    deepLinkUrlFlow = deepLinkUrlState.asStateFlow(),
                    onDeepLinkConsumed = { deepLinkUrlState.value = null },
                    onExitApp = {
                        finishAffinity()
                        Process.killProcess(Process.myPid())
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateDeepLinkIntent(intent)
    }

    /**
     * Deep Link IntentからURLを取り出して状態に反映する。
     */
    private fun updateDeepLinkIntent(intent: Intent?) {
        val url = intent?.dataString ?: return // Deep Linkが無い場合は更新しない。
        deepLinkUrlState.value = url
    }
}

/**
 * ActivityのSTARTED境界をpending result観察の開始・停止へ変換する。
 *
 * blockのcancelを含むすべての終了経路で停止callbackを呼び、STOP中の再読を許可しない。
 */
internal suspend fun observePendingRestoreResultLifecycle(
    lifecycle: Lifecycle,
    onStarted: () -> Unit,
    onStopped: () -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        onStarted()
        try {
            awaitCancellation()
        } finally {
            onStopped()
        }
    }
}
