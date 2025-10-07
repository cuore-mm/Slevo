package com.websarva.wings.android.slevo.ui.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.SlevoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicenseScreen(
    onNavigateUp: () -> Unit
) {
    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(R.string.open_source_licenses),
                onNavigateUp = onNavigateUp
            )
        }
    ) { innerPadding ->
        val libraries by rememberLibraries(R.raw.aboutlibraries)
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        )
    }
}

