package com.websarva.wings.android.slevo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.websarva.wings.android.slevo.ui.AppScaffold
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * アプリのメイン画面を構成し、Deep Linkの受信も受け持つActivity。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val tabsViewModel: TabsViewModel by viewModels()
    private val deepLinkUrlState = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- Window setup ---
        enableEdgeToEdge()
        // レイアウトをキーボード表示時にリサイズさせる（ime パディングが即座に反映されやすくなる）
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // --- Deep link initialization ---
        updateDeepLinkIntent(intent)

        // --- Compose content ---
        setContent {
            val uiState by settingsViewModel.uiState.collectAsState()

            // 2) LocalView を使って Window を取り出し、InsetsController を作成
            val view = LocalView.current
            val window = (view.context as Activity).window
            val insetsController = WindowInsetsControllerCompat(window, view)

            // 3) サイドエフェクトで毎フレーム、ステータスバーのアイコン色を制御
            SideEffect {
                // true にすると「ステータスバー背景が明るい → アイコンをダークに」なる
                insetsController.isAppearanceLightStatusBars = !uiState.isDark
            }

            SlevoTheme(darkTheme = uiState.isDark) {
                AppScaffold(
                    settingsViewModel = settingsViewModel,
                    tabsViewModel = tabsViewModel,
                    deepLinkUrlFlow = deepLinkUrlState.asStateFlow(),
                    onDeepLinkConsumed = { deepLinkUrlState.value = null }
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
