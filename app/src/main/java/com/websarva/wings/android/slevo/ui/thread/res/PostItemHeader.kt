package com.websarva.wings.android.slevo.ui.thread.res

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.theme.idColor
import com.websarva.wings.android.slevo.ui.theme.replyCountColor
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import java.util.Calendar

internal enum class PostHeaderPart {
    Name,
    Id
}

internal data class PostHeaderUiModel(
    val header: ThreadPostUiModel.Header,
    val postNum: Int,
    val idIndex: Int,
    val idTotal: Int,
    val replyFromNumbers: List<Int>,
) {
    val replyCount: Int
        get() = replyFromNumbers.size

    val idText: String
        get() = if (idTotal > 1) "${header.id} ($idIndex/$idTotal)" else header.id
}

private const val HeaderTagName = "NAME"
private const val HeaderTagId = "ID"

private data class PostHeaderTextColors(
    val onSurfaceVariant: Color,
    val pressed: Color,
    val userId: Color,
)

@Composable
internal fun PostItemHeader(
    uiModel: PostHeaderUiModel,
    headerTextStyle: TextStyle,
    lineHeightEm: Float,
    pressedHeaderPart: PostHeaderPart?,
    scope: CoroutineScope,
    haptic: HapticFeedback,
    onPressedHeaderPartChange: (PostHeaderPart?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyFromClick: ((List<Int>) -> Unit)?,
    onIdClick: ((String) -> Unit)?,
    onShowTextMenu: (text: String, type: NgType) -> Unit,
) {
    val userIdColor = idColor(uiModel.idTotal)
    val colors = PostHeaderTextColors(
        onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant,
        pressed = MaterialTheme.colorScheme.primary,
        userId = userIdColor
    )

    Row {
        PostNumberText(
            modifier = Modifier.alignByBaseline(),
            postNum = uiModel.postNum,
            replyFromNumbers = uiModel.replyFromNumbers,
            headerTextStyle = headerTextStyle,
            onReplyFromClick = onReplyFromClick
        )

        val headerText = remember(uiModel, pressedHeaderPart, colors) {
            buildHeaderText(
                uiModel = uiModel,
                pressedHeaderPart = pressedHeaderPart,
                colors = colors
            )
        }
        PostHeaderAnnotatedText(
            modifier = Modifier.alignByBaseline(),
            headerText = headerText,
            headerTextStyle = headerTextStyle,
            lineHeightEm = lineHeightEm,
            id = uiModel.header.id,
            scope = scope,
            haptic = haptic,
            onPressedHeaderPartChange = onPressedHeaderPartChange,
            onContentPressedChange = onContentPressedChange,
            onRequestMenu = onRequestMenu,
            onIdClick = onIdClick,
            onShowTextMenu = onShowTextMenu
        )
    }
}

@Composable
private fun PostNumberText(
    modifier: Modifier,
    postNum: Int,
    replyFromNumbers: List<Int>,
    headerTextStyle: TextStyle,
    onReplyFromClick: ((List<Int>) -> Unit)?,
) {
    val replyCount = replyFromNumbers.size
    val postNumColor =
        if (replyCount > 0) replyCountColor(replyCount) else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        modifier = modifier
            .clickable(enabled = replyCount > 0) {
                onReplyFromClick?.invoke(replyFromNumbers)
            },
        text = if (replyCount > 0) "$postNum ($replyCount) " else "$postNum ",
        style = headerTextStyle,
        fontWeight = FontWeight.Bold,
        color = postNumColor
    )
}

@Composable
private fun PostHeaderAnnotatedText(
    modifier: Modifier,
    headerText: AnnotatedString,
    headerTextStyle: TextStyle,
    lineHeightEm: Float,
    id: String,
    scope: CoroutineScope,
    haptic: HapticFeedback,
    onPressedHeaderPartChange: (PostHeaderPart?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onIdClick: ((String) -> Unit)?,
    onShowTextMenu: (text: String, type: NgType) -> Unit,
) {
    var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        headerLayout?.let { layout ->
                            val pos = layout.getOffsetForPosition(offset)
                            val nameAnn =
                                headerText.getStringAnnotations(HeaderTagName, pos, pos)
                                    .firstOrNull()
                            val idAnn =
                                headerText.getStringAnnotations(HeaderTagId, pos, pos).firstOrNull()
                            when {
                                nameAnn != null ->
                                    handlePressFeedback(
                                        scope = scope,
                                        feedbackDelayMillis = 0L,
                                        onFeedbackStart = {
                                            onPressedHeaderPartChange(PostHeaderPart.Name)
                                        },
                                        onFeedbackEnd = { onPressedHeaderPartChange(null) },
                                        awaitRelease = { awaitRelease() }
                                    )

                                idAnn != null ->
                                    handlePressFeedback(
                                        scope = scope,
                                        feedbackDelayMillis = 0L,
                                        onFeedbackStart = {
                                            onPressedHeaderPartChange(PostHeaderPart.Id)
                                        },
                                        onFeedbackEnd = { onPressedHeaderPartChange(null) },
                                        awaitRelease = { awaitRelease() }
                                    )

                                else ->
                                    handlePressFeedback(
                                        scope = scope,
                                        onFeedbackStart = { onContentPressedChange(true) },
                                        onFeedbackEnd = { onContentPressedChange(false) },
                                        awaitRelease = { awaitRelease() }
                                    )
                            }
                        } ?: handlePressFeedback(
                            scope = scope,
                            onFeedbackStart = { onContentPressedChange(true) },
                            onFeedbackEnd = { onContentPressedChange(false) },
                            awaitRelease = { awaitRelease() }
                        )
                    },
                    onTap = { offset ->
                        headerLayout?.let { layout ->
                            val pos = layout.getOffsetForPosition(offset)
                            headerText.getStringAnnotations(HeaderTagId, pos, pos)
                                .firstOrNull()
                                ?.let { onIdClick?.invoke(id) }
                        }
                    },
                    onLongPress = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        headerLayout?.let { layout ->
                            val pos = layout.getOffsetForPosition(offset)
                            val nameAnn =
                                headerText.getStringAnnotations(HeaderTagName, pos, pos)
                                    .firstOrNull()
                            val idAnn =
                                headerText.getStringAnnotations(HeaderTagId, pos, pos).firstOrNull()
                            when {
                                nameAnn != null -> onShowTextMenu(
                                    nameAnn.item,
                                    NgType.USER_NAME
                                )

                                idAnn != null -> onShowTextMenu(idAnn.item, NgType.USER_ID)
                                else -> onRequestMenu()
                            }
                        } ?: onRequestMenu()
                    }
                )
            },
        text = headerText,
        style = headerTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = lineHeightEm.em,
        onTextLayout = { headerLayout = it }
    )
}

private fun buildHeaderText(
    uiModel: PostHeaderUiModel,
    pressedHeaderPart: PostHeaderPart?,
    colors: PostHeaderTextColors,
): AnnotatedString {
    val displayDate = formatDisplayDate(uiModel.header.date)
    val emailDate = buildEmailDate(email = uiModel.header.email, displayDate = displayDate)
    return buildAnnotatedString {
        var first = true
        fun appendSpaceIfNeeded() {
            if (!first) append(" ") else first = false
        }

        if (uiModel.header.name.isNotBlank()) {
            appendSpaceIfNeeded()
            pushStringAnnotation(tag = HeaderTagName, annotation = uiModel.header.name)
            withStyle(
                SpanStyle(
                    color = if (pressedHeaderPart == PostHeaderPart.Name) colors.pressed else colors.onSurfaceVariant,
                    textDecoration = if (pressedHeaderPart == PostHeaderPart.Name) TextDecoration.Underline else TextDecoration.None
                )
            ) {
                append(uiModel.header.name)
            }
            pop()
        }

        if (emailDate.isNotBlank()) {
            appendSpaceIfNeeded()
            withStyle(SpanStyle(color = colors.onSurfaceVariant)) {
                append(emailDate)
            }
        }

        if (uiModel.header.id.isNotBlank()) {
            appendSpaceIfNeeded()
            pushStringAnnotation(tag = HeaderTagId, annotation = uiModel.header.id)
            withStyle(
                SpanStyle(
                    color = if (pressedHeaderPart == PostHeaderPart.Id) colors.pressed else colors.userId,
                    textDecoration = if (pressedHeaderPart == PostHeaderPart.Id) TextDecoration.Underline else TextDecoration.None
                )
            ) {
                append(uiModel.idText)
            }
            pop()
        }

        if (uiModel.header.beRank.isNotBlank()) {
            appendSpaceIfNeeded()
            withStyle(SpanStyle(color = colors.onSurfaceVariant)) {
                append(uiModel.header.beRank)
            }
        }
    }
}

private fun formatDisplayDate(date: String): String {
    val currentYearPrefix = currentYearPrefix()
    return if (date.startsWith(currentYearPrefix)) {
        date.removePrefix(currentYearPrefix)
    } else {
        date
    }
}

private fun buildEmailDate(email: String, displayDate: String): String {
    return listOf(email, displayDate).filter { it.isNotBlank() }.joinToString(" ")
}

private fun currentYearPrefix(): String {
    val year = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDate.now().year
    } else {
        Calendar.getInstance().get(Calendar.YEAR)
    }
    return "$year/"
}

@Preview(showBackground = true)
@Composable
private fun PostItemHeaderPreview() {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressedHeaderPart by remember { mutableStateOf<PostHeaderPart?>(null) }

    PostItemHeader(
        uiModel = PostHeaderUiModel(
            header = ThreadPostUiModel.Header(
                name = "風吹けば名無し",
                email = "sage",
                date = "2025/12/16(火) 12:34:56.78",
                id = "testid",
                beRank = "PLT(2000)",
            ),
            postNum = 12,
            idIndex = 1,
            idTotal = 3,
            replyFromNumbers = listOf(1, 2, 3),
        ),
        headerTextStyle = MaterialTheme.typography.bodyMedium,
        lineHeightEm = 1.4f,
        pressedHeaderPart = pressedHeaderPart,
        scope = scope,
        haptic = haptic,
        onPressedHeaderPartChange = { pressedHeaderPart = it },
        onContentPressedChange = {},
        onRequestMenu = {},
        onReplyFromClick = {},
        onIdClick = {},
        onShowTextMenu = { _, _ -> },
    )
}
