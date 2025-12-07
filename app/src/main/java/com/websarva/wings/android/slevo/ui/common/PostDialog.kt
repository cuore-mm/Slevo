package com.websarva.wings.android.slevo.ui.common

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.util.extractImageUrls

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PostDialog(
    onDismissRequest: () -> Unit,
    name: String,
    mail: String,
    message: String,
    namePlaceholder: String,
    nameHistory: List<String>,
    mailHistory: List<String>,
    onNameChange: (String) -> Unit,
    onMailChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onNameHistorySelect: (String) -> Unit,
    onMailHistorySelect: (String) -> Unit,
    onNameHistoryDelete: (String) -> Unit,
    onMailHistoryDelete: (String) -> Unit,
    onPostClick: () -> Unit,
    confirmButtonText: String,
    title: String? = null,
    onTitleChange: ((String) -> Unit)? = null,
    onImageSelect: ((Uri) -> Unit)? = null,
    onImageUrlClick: ((String) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val launcher = rememberImagePickerLauncher(
        onImageSelect = onImageSelect
    )

    val content: @Composable () -> Unit = {
        PostDialogContent(
            name = name,
            mail = mail,
            message = message,
            namePlaceholder = namePlaceholder,
            nameHistory = nameHistory,
            mailHistory = mailHistory,
            onNameChange = onNameChange,
            onMailChange = onMailChange,
            onMessageChange = onMessageChange,
            onNameHistorySelect = onNameHistorySelect,
            onMailHistorySelect = onMailHistorySelect,
            onNameHistoryDelete = onNameHistoryDelete,
            onMailHistoryDelete = onMailHistoryDelete,
            onPostClick = onPostClick,
            confirmButtonText = confirmButtonText,
            title = title,
            onTitleChange = onTitleChange,
            onImageUrlClick = onImageUrlClick,
            launcher = launcher,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

    Dialog(onDismissRequest = onDismissRequest) { content() }

}

@Composable
private fun rememberImagePickerLauncher(
    onImageSelect: ((Uri) -> Unit)?,
): ManagedActivityResultLauncher<String, Uri?> {
    return rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImageSelect?.invoke(uri)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PostDialogContent(
    name: String,
    mail: String,
    message: String,
    namePlaceholder: String,
    nameHistory: List<String>,
    mailHistory: List<String>,
    onNameChange: (String) -> Unit,
    onMailChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onNameHistorySelect: (String) -> Unit,
    onMailHistorySelect: (String) -> Unit,
    onNameHistoryDelete: (String) -> Unit,
    onMailHistoryDelete: (String) -> Unit,
    onPostClick: () -> Unit,
    confirmButtonText: String,
    title: String?,
    onTitleChange: ((String) -> Unit)?,
    onImageUrlClick: ((String) -> Unit)?,
    launcher: ManagedActivityResultLauncher<String, Uri?>?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.imePadding()
    ) {
        Column {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
            ) {
                HeaderInputSection(
                    name = name,
                    mail = mail,
                    namePlaceholder = namePlaceholder,
                    nameHistory = nameHistory,
                    mailHistory = mailHistory,
                    onNameChange = onNameChange,
                    onMailChange = onMailChange,
                    onNameHistorySelect = onNameHistorySelect,
                    onMailHistorySelect = onMailHistorySelect,
                    onNameHistoryDelete = onNameHistoryDelete,
                    onMailHistoryDelete = onMailHistoryDelete
                )
                BodyInputSection(
                    title = title,
                    onTitleChange = onTitleChange,
                    message = message,
                    onMessageChange = onMessageChange,
                    focusRequester = focusRequester,
                    onImageUrlClick = onImageUrlClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
            BottomActionRow(launcher = launcher)
            PostButton(
                confirmButtonText = confirmButtonText,
                isEnabled = if (title != null && onTitleChange != null) {
                    title.isNotBlank() && message.isNotBlank()
                } else {
                    message.isNotBlank()
                },
                onPostClick = onPostClick
            )
        }
    }
}

@Composable
private fun HeaderInputSection(
    name: String,
    mail: String,
    namePlaceholder: String,
    nameHistory: List<String>,
    mailHistory: List<String>,
    onNameChange: (String) -> Unit,
    onMailChange: (String) -> Unit,
    onNameHistorySelect: (String) -> Unit,
    onMailHistorySelect: (String) -> Unit,
    onNameHistoryDelete: (String) -> Unit,
    onMailHistoryDelete: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        HistoryDropdownTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = {
                Text(
                    text = namePlaceholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            history = remember(nameHistory, name) { nameHistory.filter { it != name } },
            onHistorySelect = onNameHistorySelect,
            onHistoryDelete = onNameHistoryDelete,
            focusManager = focusManager,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        HistoryDropdownTextField(
            value = mail,
            onValueChange = onMailChange,
            placeholder = { Text(stringResource(R.string.e_mail)) },
            history = remember(mailHistory, mail) { mailHistory.filter { it != mail } },
            onHistorySelect = onMailHistorySelect,
            onHistoryDelete = onMailHistoryDelete,
            focusManager = focusManager,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HistoryDropdownTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: @Composable () -> Unit,
    history: List<String>,
    onHistorySelect: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    focusManager: FocusManager,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var isFocused by remember { mutableStateOf(false) }
    var textFieldWidth by remember { mutableStateOf(0.dp) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                }
                .onGloballyPositioned { coordinates ->
                    textFieldWidth = with(density) {
                        coordinates.size.width.toDp()
                    }
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) }
            )
        )
        DropdownMenu(
            expanded = isFocused && history.isNotEmpty(),
            onDismissRequest = {
                isFocused = false
                focusManager.clearFocus()
            },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.width(textFieldWidth)
        ) {
            history.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onValueChange(value)
                        onHistorySelect(value)
                        isFocused = false
                        focusManager.clearFocus()
                    },
                    trailingIcon = {
                        IconButton(onClick = { onHistoryDelete(value) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BodyInputSection(
    title: String?,
    onTitleChange: ((String) -> Unit)?,
    message: String,
    onMessageChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onImageUrlClick: ((String) -> Unit)?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    if (title != null && onTitleChange != null) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = { Text(stringResource(R.string.title)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
    }

    var messageValue by remember { mutableStateOf(TextFieldValue(message)) }
    val targetMessageValue = remember(message) {
        TextFieldValue(
            text = message,
            selection = TextRange(message.length)
        )
    }
    if (messageValue.text != message) {
        messageValue = targetMessageValue
    }

    OutlinedTextField(
        value = messageValue,
        onValueChange = {
            messageValue = it
            onMessageChange(it.text)
        },
        placeholder = { Text(stringResource(R.string.post_message)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .focusRequester(focusRequester),
        minLines = 3,
    )

    val imageUrls = remember(message) { extractImageUrls(message) }
    if (imageUrls.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        ImageThumbnailGrid(
            modifier = Modifier.padding(horizontal = 8.dp),
            imageUrls = imageUrls,
            onImageClick = { url -> onImageUrlClick?.invoke(url) },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

@Composable
private fun BottomActionRow(
    launcher: ManagedActivityResultLauncher<String, Uri?>?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(
            onClick = { launcher?.launch("image/*") },
            enabled = launcher != null
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = stringResource(id = R.string.select_image)
            )
        }
    }
}

@Composable
private fun PostButton(
    confirmButtonText: String,
    isEnabled: Boolean,
    onPostClick: () -> Unit,
) {
    Button(
        onClick = onPostClick,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(text = confirmButtonText)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun PostDialogPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            PostDialogContent(
                name = "",
                mail = "",
                message = "",
                namePlaceholder = "風吹けば名無し",
                nameHistory = listOf(""),
                mailHistory = listOf("sage", "mail@example.com"),
                onNameChange = { },
                onMailChange = { },
                onMessageChange = { },
                onNameHistorySelect = {},
                onMailHistorySelect = {},
                onNameHistoryDelete = {},
                onMailHistoryDelete = {},
                onPostClick = { },
                confirmButtonText = "",
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onTitleChange = null,
                onImageUrlClick = null,
                launcher = null,
                title = null,
            )
        }
    }
}
