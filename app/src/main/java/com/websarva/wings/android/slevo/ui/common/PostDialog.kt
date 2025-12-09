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
import com.websarva.wings.android.slevo.ui.thread.state.PostDialogAction
import com.websarva.wings.android.slevo.ui.thread.state.PostUiState
import com.websarva.wings.android.slevo.ui.util.extractImageUrls

enum class PostDialogMode {
    Reply,
    NewThread,
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PostDialog(
    uiState: PostUiState,
    onDismissRequest: () -> Unit,
    onAction: (PostDialogAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onImageUpload: ((Uri) -> Unit),
    onImageUrlClick: ((String) -> Unit),
    mode: PostDialogMode,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        PostDialogContent(
            uiState = uiState,
            onAction = onAction,
            onImageUpload = onImageUpload,
            onImageUrlClick = onImageUrlClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            mode = mode
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PostDialogContent(
    uiState: PostUiState,
    onAction: (PostDialogAction) -> Unit,
    onImageUpload: (Uri) -> Unit,
    onImageUrlClick: ((String) -> Unit)?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    mode: PostDialogMode,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val namePlaceholder = uiState.namePlaceholder.ifBlank { stringResource(R.string.name) }
    val confirmButtonText = when (mode) {
        PostDialogMode.NewThread -> stringResource(R.string.create_thread)
        PostDialogMode.Reply -> stringResource(R.string.post)
    }

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
                    name = uiState.postFormState.name,
                    mail = uiState.postFormState.mail,
                    namePlaceholder = namePlaceholder,
                    nameHistory = uiState.nameHistory,
                    mailHistory = uiState.mailHistory,
                    onNameChange = { onAction(PostDialogAction.ChangeName(it)) },
                    onMailChange = { onAction(PostDialogAction.ChangeMail(it)) },
                    onNameHistorySelect = { onAction(PostDialogAction.SelectNameHistory(it)) },
                    onMailHistorySelect = { onAction(PostDialogAction.SelectMailHistory(it)) },
                    onNameHistoryDelete = { onAction(PostDialogAction.DeleteNameHistory(it)) },
                    onMailHistoryDelete = { onAction(PostDialogAction.DeleteMailHistory(it)) }
                )
                BodyInputSection(
                    title = uiState.postFormState.title,
                    mode = mode,
                    onTitleChange = { onAction(PostDialogAction.ChangeTitle(it)) },
                    message = uiState.postFormState.message,
                    onMessageChange = { onAction(PostDialogAction.ChangeMessage(it)) },
                    focusRequester = focusRequester,
                    onImageUrlClick = onImageUrlClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
            BottomActionRow(onImageUpload = onImageUpload)
            PostButton(
                confirmButtonText = confirmButtonText,
                isEnabled = when (mode) {
                    PostDialogMode.NewThread ->
                        uiState.postFormState.title.isNotBlank() && uiState.postFormState.message.isNotBlank()
                    PostDialogMode.Reply -> uiState.postFormState.message.isNotBlank()
                },
                onPostClick = { onAction(PostDialogAction.Post) }
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
                    textFieldWidth = with(density) { coordinates.size.width.toDp() }
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
    title: String,
    mode: PostDialogMode,
    onTitleChange: (String) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onImageUrlClick: ((String) -> Unit)?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    if (mode == PostDialogMode.NewThread) {
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
    onImageUpload: ((Uri) -> Unit),
) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onImageUpload.invoke(uri)
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(
            onClick = { launcher.launch("image/*") },
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
                uiState = PostUiState(namePlaceholder = "name"),
                onAction = {},
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onImageUpload = {},
                onImageUrlClick = {},
                mode = PostDialogMode.NewThread
            )
        }
    }
}
