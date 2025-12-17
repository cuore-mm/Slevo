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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.theme.idColor
import com.websarva.wings.android.slevo.ui.theme.replyCountColor
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import java.util.Calendar

@Composable
internal fun PostItemHeader(
    post: ReplyInfo,
    postNum: Int,
    idIndex: Int,
    idTotal: Int,
    replyFromNumbers: List<Int>,
    headerTextStyle: TextStyle,
    lineHeightEm: Float,
    pressedHeaderPart: String?,
    scope: CoroutineScope,
    haptic: HapticFeedback,
    onPressedHeaderPartChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyFromClick: ((List<Int>) -> Unit)?,
    onIdClick: ((String) -> Unit)?,
    onShowTextMenu: (text: String, type: NgType) -> Unit,
) {
    val idText = if (idTotal > 1) "${post.id} (${idIndex}/${idTotal})" else post.id
    val userIdColor = idColor(idTotal)

    Row {
        val replyCount = replyFromNumbers.size
        val postNumColor =
            if (replyCount > 0) replyCountColor(replyCount) else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            modifier = Modifier
                .alignByBaseline()
                .clickable(enabled = replyCount > 0) {
                    onReplyFromClick?.invoke(replyFromNumbers)
                },
            text = if (replyCount > 0) "$postNum ($replyCount) " else "$postNum ",
            style = headerTextStyle,
            fontWeight = FontWeight.Bold,
            color = postNumColor
        )

        val headerText = buildAnnotatedString {
            var first = true
            fun appendSpaceIfNeeded() {
                if (!first) append(" ") else first = false
            }

            if (post.name.isNotBlank()) {
                appendSpaceIfNeeded()
                pushStringAnnotation(tag = "NAME", annotation = post.name)
                withStyle(
                    SpanStyle(
                        color = if (pressedHeaderPart == "NAME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (pressedHeaderPart == "NAME") TextDecoration.Underline else TextDecoration.None
                    )
                ) {
                    append(post.name)
                }
                pop()
            }

            val currentYearPrefix = run {
                val year = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDate.now().year
                } else {
                    Calendar.getInstance().get(Calendar.YEAR)
                }
                "$year/"
            }
            val displayDate =
                if (post.date.startsWith(currentYearPrefix)) {
                    post.date.removePrefix(currentYearPrefix)
                } else {
                    post.date
                }
            val emailDate =
                listOf(post.email, displayDate).filter { it.isNotBlank() }
                    .joinToString(" ")
            if (emailDate.isNotBlank()) {
                appendSpaceIfNeeded()
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(emailDate)
                }
            }

            if (post.id.isNotBlank()) {
                appendSpaceIfNeeded()
                pushStringAnnotation(tag = "ID", annotation = post.id)
                withStyle(
                    SpanStyle(
                        color = if (pressedHeaderPart == "ID") MaterialTheme.colorScheme.primary else userIdColor,
                        textDecoration = if (pressedHeaderPart == "ID") TextDecoration.Underline else TextDecoration.None
                    )
                ) {
                    append(idText)
                }
                pop()
            }

            if (post.beRank.isNotBlank()) {
                appendSpaceIfNeeded()
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(post.beRank)
                }
            }
        }

        var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            modifier = Modifier
                .alignByBaseline()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            headerLayout?.let { layout ->
                                val pos = layout.getOffsetForPosition(offset)
                                val nameAnn =
                                    headerText.getStringAnnotations("NAME", pos, pos).firstOrNull()
                                val idAnn =
                                    headerText.getStringAnnotations("ID", pos, pos).firstOrNull()
                                when {
                                    nameAnn != null ->
                                        handlePressFeedback(
                                            scope = scope,
                                            feedbackDelayMillis = 0L,
                                            onFeedbackStart = { onPressedHeaderPartChange("NAME") },
                                            onFeedbackEnd = { onPressedHeaderPartChange(null) },
                                            awaitRelease = { awaitRelease() }
                                        )

                                    idAnn != null ->
                                        handlePressFeedback(
                                            scope = scope,
                                            feedbackDelayMillis = 0L,
                                            onFeedbackStart = { onPressedHeaderPartChange("ID") },
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
                                headerText.getStringAnnotations("ID", pos, pos)
                                    .firstOrNull()
                                    ?.let { onIdClick?.invoke(post.id) }
                            }
                        },
                        onLongPress = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            headerLayout?.let { layout ->
                                val pos = layout.getOffsetForPosition(offset)
                                val nameAnn =
                                    headerText.getStringAnnotations("NAME", pos, pos).firstOrNull()
                                val idAnn =
                                    headerText.getStringAnnotations("ID", pos, pos).firstOrNull()
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
}

@Preview(showBackground = true)
@Composable
private fun PostItemHeaderPreview() {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressedHeaderPart by remember { mutableStateOf<String?>(null) }

    PostItemHeader(
        post = ReplyInfo(
            name = "風吹けば名無し",
            email = "sage",
            date = "2025/12/16(火) 12:34:56.78",
            id = "testid",
            beRank = "PLT(2000)",
            content = "本文"
        ),
        postNum = 12,
        idIndex = 1,
        idTotal = 3,
        replyFromNumbers = listOf(1, 2, 3),
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
