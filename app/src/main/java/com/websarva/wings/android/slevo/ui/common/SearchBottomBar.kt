package com.websarva.wings.android.slevo.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R

/**
 * 検索モード時に表示するボトムバーを提供する。
 *
 * 検索入力と音声入力の操作をまとめ、既存の検索フローを維持する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomBar(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    @StringRes placeholderResId: Int = R.string.search,
) {
    FlexibleBottomAppBar(
        modifier = modifier,
    ) {
        Card {
            SearchInputField(
                searchQuery = searchQuery,
                onQueryChange = onQueryChange,
                onCloseSearch = onCloseSearch,
                placeholderResId = placeholderResId,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun SearchBottomBarPreview() {
    SearchBottomBar(
        searchQuery = "",
        onQueryChange = {},
        onCloseSearch = {},
        placeholderResId = R.string.search_in_thread,
    )
}
