package com.websarva.wings.android.slevo

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.websarva.wings.android.slevo.ui.AppScaffold
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val tabsViewModel: TabsViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    tabsViewModel = tabsViewModel
                )
            }
        }
    }
}
