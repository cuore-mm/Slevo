package com.websarva.wings.android.bbsviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.websarva.wings.android.bbsviewer.ui.HomeScreen
import com.websarva.wings.android.bbsviewer.ui.theme.BBSViewerTheme
import com.websarva.wings.android.bbsviewer.ui.ThreadViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ThreadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BBSViewerTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}
