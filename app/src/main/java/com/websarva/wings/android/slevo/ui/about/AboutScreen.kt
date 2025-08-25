package com.websarva.wings.android.slevo.ui.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.websarva.wings.android.slevo.BuildConfig
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.SmallTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME
    val githubUrl = stringResource(R.string.github_url)

    Scaffold(
        topBar = {
            SmallTopAppBarScreen(
                title = stringResource(R.string.about_this_app),
                onNavigateUp = onNavigateUp
            )
        }
    ) { innerPadding ->

        val libraries by rememberLibraries(R.raw.aboutlibraries)
        LibrariesContainer(libraries, Modifier.fillMaxSize())
    }
}
