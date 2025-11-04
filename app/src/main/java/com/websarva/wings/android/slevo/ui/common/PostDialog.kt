package com.websarva.wings.android.slevo.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
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
    onImageSelect: ((android.net.Uri) -> Unit)? = null,
    onImageUrlClick: ((String) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // Preview（Inspection）環境かどうか
    val isPreview = LocalInspectionMode.current
    // Preview では ActivityResultRegistryOwner が無くクラッシュするので生成しない
    val launcher = if (!isPreview) {
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onImageSelect?.invoke(uri)
        }
    } else null

    val content: @Composable () -> Unit = {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }

        // ダイアログの内容をCardで包むことで見た目を整える
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.imePadding()
        ) {
            Column {
                // 画像ボタンより上をスクロール可能にする
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        val focusManager = LocalFocusManager.current
                        val density = LocalDensity.current
                        Column(modifier = Modifier.weight(1f)) {
                            var isNameFocused by remember { mutableStateOf(false) }
                            var nameTextFieldWidth by remember { mutableStateOf(0.dp) }
                            val filteredNameHistory = remember(nameHistory, name) {
                                nameHistory.filter { it != name }
                            }
                            Box {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { onNameChange(it) },
                                    placeholder = {
                                        Text(
                                            text = namePlaceholder,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            isNameFocused = focusState.isFocused
                                        }
                                        .onGloballyPositioned { coordinates ->
                                            nameTextFieldWidth = with(density) {
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
                                    expanded = isNameFocused && filteredNameHistory.isNotEmpty(),
                                    onDismissRequest = {
                                        isNameFocused = false
                                        focusManager.clearFocus()
                                    },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier.width(nameTextFieldWidth)
                                ) {
                                    filteredNameHistory.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                onNameChange(value)
                                                onNameHistorySelect(value)
                                                isNameFocused = false
                                                focusManager.clearFocus()
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = { onNameHistoryDelete(value) }) {
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            var isMailFocused by remember { mutableStateOf(false) }
                            var mailTextFieldWidth by remember { mutableStateOf(0.dp) }
                            val mailFilteredHistory = remember(mailHistory, mail) {
                                mailHistory.filter { it != mail }
                            }
                            Box {
                                OutlinedTextField(
                                    value = mail,
                                    onValueChange = { onMailChange(it) },
                                    placeholder = { Text(stringResource(R.string.e_mail)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            isMailFocused = focusState.isFocused
                                        }
                                        .onGloballyPositioned { coordinates ->
                                            mailTextFieldWidth = with(density) {
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
                                    expanded = isMailFocused && mailFilteredHistory.isNotEmpty(),
                                    onDismissRequest = {
                                        isMailFocused = false
                                        focusManager.clearFocus()
                                    },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier.width(mailTextFieldWidth)
                                ) {
                                    mailFilteredHistory.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                onMailChange(value)
                                                onMailHistorySelect(value)
                                                isMailFocused = false
                                                focusManager.clearFocus()
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = { onMailHistoryDelete(value) }) {
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
                    }

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

                    var messageValue by remember {
                        mutableStateOf(TextFieldValue(message))
                    }
                    var initialized by remember { mutableStateOf(false) }

                    LaunchedEffect(message) {
                        if (!initialized) {
                            messageValue = TextFieldValue(
                                text = message,
                                selection = TextRange(message.length) // ← 末尾にカーソル
                            )
                            initialized = true
                        }
                    }

                    OutlinedTextField(
                        value = messageValue,
                        onValueChange = {
                            messageValue = it
                            // 親側は String で管理しているのでテキストのみ渡す
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
                            modifier = Modifier
                                .padding(horizontal = 8.dp),
                            imageUrls = imageUrls,
                            onImageClick = { url -> onImageUrlClick?.invoke(url) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }

                // 非スクロール領域（常に表示）
                Row(modifier = Modifier.fillMaxWidth()) {
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

                val isPostButtonEnabled = if (title != null && onTitleChange != null) {
                    title.isNotBlank() && message.isNotBlank()
                } else {
                    message.isNotBlank()
                }

                Button(
                    onClick = { onPostClick() },
                    enabled = isPostButtonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(text = confirmButtonText)
                }
            }
        }
    }

    if (isPreview) {
        content()
    } else {
        Dialog(onDismissRequest = onDismissRequest) { content() }
    }
}

@Preview(showBackground = true)
@Composable
fun PostDialogPreview() {
    PostDialog(
        onDismissRequest = { /* ダイアログを閉じる処理 */ },
        name = "",
        mail = "",
        message = "https://i.imgur.com/Btor6RG.jpeg",
        namePlaceholder = "それでも動く名無し",
        nameHistory = listOf("太郎", "名無し"),
        mailHistory = listOf("sage", "mail@example.com"),
        onNameChange = { /* 名前変更処理 */ },
        onMailChange = { /* メール変更処理 */ },
        onMessageChange = { /* メッセージ変更処理 */ },
        onNameHistorySelect = {},
        onMailHistorySelect = {},
        onNameHistoryDelete = {},
        onMailHistoryDelete = {},
        onPostClick = { /* 投稿処理 */ },
        confirmButtonText = "書き込み",
        onImageSelect = { }
    )
}
