package com.websarva.wings.android.slevo.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.text.input.TextFieldValue
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
    searchInputValue: TextFieldValue,
    onSearchInputChange: (TextFieldValue) -> Unit,
    onCloseSearch: () -> Unit,
    @StringRes placeholderResId: Int = R.string.search,
) {
    FlexibleBottomAppBar(
        modifier = modifier,
    ) {
        Card {
            SearchInputField(
                searchInputValue = searchInputValue,
                onSearchInputChange = onSearchInputChange,
                onCloseSearch = onCloseSearch,
                focusRequestId = null,
                onFocusRequestConsumed = null,
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
        searchInputValue = TextFieldValue(""),
        onSearchInputChange = {},
        onCloseSearch = {},
        placeholderResId = R.string.search_in_thread,
    )
}
