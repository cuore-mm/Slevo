package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.slevo.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostingDialog() {
    Dialog(onDismissRequest = {}) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.posting_in_progress))
                Spacer(modifier = Modifier.height(16.dp))
                LoadingIndicator()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PostingDialogPreview() {
    PostingDialog()
}
