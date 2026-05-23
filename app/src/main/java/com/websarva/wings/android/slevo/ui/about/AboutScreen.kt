package com.websarva.wings.android.slevo.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GitHub
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.BuildConfig
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.settings.SettingsCardWithListItems
import com.websarva.wings.android.slevo.ui.settings.listItemSpecOfBasic

/**
 * アプリ情報を表示する画面。
 *
 * 画面上部にアプリアイコン・アプリ名・バージョンを中央揃えで表示し、
 * 下部に GitHub、ログ共有、OSS ライセンスの操作項目をカード形式で表示する。
 *
 * @param onNavigateUp 戻る遷移のコールバック
 * @param onOpenSourceLicenseClick OSS ライセンス画面への遷移コールバック
 * @param onShareLogClick ログ共有のコールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateUp: () -> Unit,
    onOpenSourceLicenseClick: () -> Unit,
    onShareLogClick: () -> Unit
) {
    val versionName = BuildConfig.VERSION_NAME
    val githubUrl = stringResource(R.string.github_url)
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(R.string.about_this_app),
                onNavigateUp = onNavigateUp
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            // --- App Info Header ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_name_label, versionName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // --- Action Items Card ---
            item {
                val items = listOf(
                    listItemSpecOfBasic(
                        headlineText = stringResource(R.string.github),
                        supportingText = githubUrl,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.GitHub,
                                contentDescription = stringResource(R.string.github),
                            )
                        },
                        onClick = { uriHandler.openUri(githubUrl) },
                    ),
                    listItemSpecOfBasic(
                        headlineText = stringResource(R.string.share_log),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.share_log),
                            )
                        },
                        onClick = onShareLogClick,
                    ),
                    listItemSpecOfBasic(
                        headlineText = stringResource(R.string.open_source_licenses),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = stringResource(R.string.open_source_licenses),
                            )
                        },
                        onClick = onOpenSourceLicenseClick,
                    ),
                )
                SettingsCardWithListItems(items = items)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    MaterialTheme {
        AboutScreen(
            onNavigateUp = {},
            onOpenSourceLicenseClick = {},
            onShareLogClick = {}
        )
    }
}
