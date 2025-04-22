package com.websarva.wings.android.bbsviewer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.ui.AppScaffold
import com.websarva.wings.android.bbsviewer.ui.bbslist.board.BbsBoardViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.category.BbsCategoryViewModel
import com.websarva.wings.android.bbsviewer.ui.bbslist.service.BbsServiceViewModel
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.theme.BBSViewerTheme
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val topAppBarViewModel: TopAppBarViewModel by viewModels()
    private val bookmarkViewModel: BookmarkViewModel by viewModels()
    private val bbsServiceViewModel: BbsServiceViewModel by viewModels()
    private val bbsCategoryViewModel: BbsCategoryViewModel by viewModels()
    private val bbsBoardViewModel: BbsBoardViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BBSViewerTheme {
                AppScaffold(
                    topAppBarViewModel = topAppBarViewModel,
                    bookmarkViewModel = bookmarkViewModel,
                    bbsServiceViewModel = bbsServiceViewModel,
                    bbsCategoryViewModel = bbsCategoryViewModel,
                    bbsBoardViewModel = bbsBoardViewModel
                )
            }
        }
    }
}
