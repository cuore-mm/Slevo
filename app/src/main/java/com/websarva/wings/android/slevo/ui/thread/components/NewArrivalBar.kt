package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R

@Composable
fun NewArrivalBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        HorizontalDivider(modifier = Modifier.align(Alignment.Center))
        Text(
            text = stringResource(id = R.string.new_responses),
            modifier = Modifier
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

