package com.websarva.wings.android.bbsviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.websarva.wings.android.bbsviewer.ui.HomeScreen
import com.websarva.wings.android.bbsviewer.ui.bbslist.BBSListViewModel
import com.websarva.wings.android.bbsviewer.ui.theme.BBSViewerTheme
import com.websarva.wings.android.bbsviewer.ui.bookmark.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.appbar.TopAppBarViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val threadViewModel: ThreadViewModel by viewModels()
    private val BBSListViewModel: BBSListViewModel by viewModels()
    private val topAppBarViewModel: TopAppBarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BBSViewerTheme {
                HomeScreen(
                    threadViewModel = threadViewModel,
                    bbsListViewModel = BBSListViewModel,
                    topAppBarViewModel = topAppBarViewModel
                )
            }
        }
    }
}
