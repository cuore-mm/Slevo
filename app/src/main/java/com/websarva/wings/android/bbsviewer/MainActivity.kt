package com.websarva.wings.android.bbsviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.websarva.wings.android.bbsviewer.ui.HomeScreen
import com.websarva.wings.android.bbsviewer.ui.theme.BBSViewerTheme
import com.websarva.wings.android.bbsviewer.ui.ThreadFetcherScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BBSViewerTheme {
                HomeScreen()
            }
        }
    }
}