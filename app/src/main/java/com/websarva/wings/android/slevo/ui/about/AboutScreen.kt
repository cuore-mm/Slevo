package com.websarva.wings.android.slevo.ui.about

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.websarva.wings.android.slevo.BuildConfig
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.SmallTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            SmallTopAppBarScreen(
                title = stringResource(R.string.about_app),
                onNavigateUp = onNavigateUp
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = {
                    Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME))
                }
            )
            ListItem(
                modifier = Modifier.clickable {
                    uriHandler.openUri(context.getString(R.string.github_url))
                },
                headlineContent = { Text(stringResource(R.string.github)) },
                supportingContent = { Text(stringResource(R.string.github_url)) }
            )
            ListItem(
                modifier = Modifier.clickable {
                    OssLicensesMenuActivity.setActivityTitle(
                        context.getString(R.string.open_source_licenses)
                    )
                    context.startActivity(
                        Intent(context, OssLicensesMenuActivity::class.java)
                    )
                },
                headlineContent = { Text(stringResource(R.string.open_source_licenses)) }
            )
        }
    }
}
