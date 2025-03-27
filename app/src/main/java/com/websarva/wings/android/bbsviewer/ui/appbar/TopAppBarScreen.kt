package com.websarva.wings.android.bbsviewer.ui.appbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController

@Composable
fun TopAppBarScreen(
    viewModel: TopAppBarViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isCenter = uiState.isCenter
    val title1 = uiState.title

    if (isCenter) {
        CenterAlignedTopAppBarScreen(
            title = title1,
            modifier = modifier
        )
    } else {
        SmallTopAppBarScreen(
            title = title1,
            onNavigateUp = { navController.navigateUp() },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterAlignedTopAppBarScreen(
    title: String,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBarScreen(
    modifier: Modifier = Modifier,
    title: String,
    onNavigateUp: () -> Unit, // 戻る処理のためのコールバック
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { // 左端にボタンを追加
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun CenterAlignedTopAppBarScreenPreview() {
    CenterAlignedTopAppBarScreen(
        title = "お気に入り"
    )
}

@Preview(showBackground = true)
@Composable
fun SmallTopAppBarScreenPreview() {
    SmallTopAppBarScreen(
        title = "お気に入り",
        onNavigateUp = { /* 戻る処理 */ }
    )
}
