package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.SlevoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCookieScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsCookieViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(R.string.cookie_management),
                onNavigateUp = onNavigateUp,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            items(
                items = uiState.cookies,
                key = { it.name + it.domain + it.path }
            ) { cookie ->
                ListItem(
                    headlineContent = { Text(cookie.name) },
                    supportingContent = {
                        Column {
                            Text(
                                stringResource(
                                    R.string.cookie_domain_path,
                                    cookie.domain,
                                    cookie.path,
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.cookie_expires,
                                    cookie.expires,
                                )
                            )
                            Text(stringResource(R.string.cookie_size, cookie.size))
                            Text(
                                stringResource(
                                    R.string.cookie_value_preview,
                                    cookie.valuePreview,
                                )
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeCookie(cookie) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

