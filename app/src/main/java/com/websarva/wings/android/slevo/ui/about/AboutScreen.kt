package com.websarva.wings.android.slevo.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.BuildConfig
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.SlevoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateUp: () -> Unit,
    onOpenSourceLicenseClick: () -> Unit
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
                .fillMaxSize()
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.version_name_label, versionName)) }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { uriHandler.openUri(githubUrl) },
                    headlineContent = { Text(stringResource(R.string.github)) },
                    supportingContent = { Text(githubUrl) }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSourceLicenseClick),
                    headlineContent = { Text(stringResource(R.string.open_source_licenses)) }
                )
            }
        }
    }
}

