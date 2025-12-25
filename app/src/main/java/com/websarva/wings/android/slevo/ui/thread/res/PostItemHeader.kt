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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
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

/**
 * 投稿ヘッダー内のタップ対象となる部位を表す。
 *
 * [tag] は AnnotatedString の注釈キーとして使用する。
 */
internal enum class PostHeaderPart(
    val tag: String,
) {
    Name("NAME"),
    Id("ID")
}

/**
 * 投稿ヘッダー表示に必要な値をまとめたUIモデル。
 *
 * 表示用の派生値をプロパティとして提供する。
 */
internal data class PostHeaderUiModel(
    val header: ThreadPostUiModel.Header,
    val postNum: Int,
    val idIndex: Int,
    val idTotal: Int,
    val replyFromNumbers: List<Int>,
) {
    // 複数ID表示時にインデックス/総数を付与したID文字列。
    val idText: String
        get() = if (idTotal > 1) "${header.id} ($idIndex/$idTotal)" else header.id
}

/**
 * 投稿ヘッダーで使用する文字色のセット。
 */
private data class PostHeaderTextColors(
    val onSurfaceVariant: Color,
    val pressed: Color,
    val userId: Color,
)

/**
 * ヘッダー上のタップ位置に対応する部位と文字列。
 */
private data class HeaderHit(
    val part: PostHeaderPart?,
    val text: String?,
)

/**
 * 投稿のヘッダー行を表示する。
 *
 * 投稿番号とヘッダーテキストを並べ、押下/長押しの結果を親へ通知する。
 */
@Composable
internal fun PostItemHeader(
    uiModel: PostHeaderUiModel,
    headerTextStyle: TextStyle,
    lineHeightEm: Float,
    pressedHeaderPart: PostHeaderPart?,
    scope: CoroutineScope,
    onPressedHeaderPartChange: (PostHeaderPart?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyFromClick: ((List<Int>) -> Unit),
    onIdClick: ((String) -> Unit),
    onShowTextMenu: (text: String, type: NgType) -> Unit,
) {
    // --- 色設定 ---
    val userIdColor = idColor(uiModel.idTotal)
    val colors = PostHeaderTextColors(
        onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant,
        pressed = MaterialTheme.colorScheme.primary,
        userId = userIdColor
    )

    Row {
        // --- 投稿番号 ---
        PostNumberText(
            modifier = Modifier.alignByBaseline(),
            postNum = uiModel.postNum,
            replyFromNumbers = uiModel.replyFromNumbers,
            headerTextStyle = headerTextStyle,
            onReplyFromClick = onReplyFromClick
        )

        // --- ヘッダー本文 ---
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
            onPressedHeaderPartChange = onPressedHeaderPartChange,
            onContentPressedChange = onContentPressedChange,
            onRequestMenu = onRequestMenu,
            onIdClick = onIdClick,
            onShowTextMenu = onShowTextMenu
        )
    }
}

/**
 * 投稿番号と返信数を表示する。
 *
 * 返信数がある場合のみタップ可能にする。
 */
@Composable
private fun PostNumberText(
    modifier: Modifier,
    postNum: Int,
    replyFromNumbers: List<Int>,
    headerTextStyle: TextStyle,
    onReplyFromClick: ((List<Int>) -> Unit),
) {
    val replyCount = replyFromNumbers.size
    val postNumColor =
        if (replyCount > 0) replyCountColor(replyCount) else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        modifier = modifier
            .clickable(enabled = replyCount > 0) {
                onReplyFromClick.invoke(replyFromNumbers)
            },
        text = if (replyCount > 0) "$postNum ($replyCount) " else "$postNum ",
        style = headerTextStyle,
        fontWeight = FontWeight.Bold,
        color = postNumColor
    )
}

/**
 * ヘッダー本文のタップ判定と描画を担当する。
 *
 * 位置に応じた押下状態やメニュー表示を切り替える。
 */
@Composable
private fun PostHeaderAnnotatedText(
    modifier: Modifier,
    headerText: AnnotatedString,
    headerTextStyle: TextStyle,
    lineHeightEm: Float,
    id: String,
    scope: CoroutineScope,
    onPressedHeaderPartChange: (PostHeaderPart?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onIdClick: ((String) -> Unit),
    onShowTextMenu: (text: String, type: NgType) -> Unit,
) {
    // --- フィードバック ---
    val haptic = LocalHapticFeedback.current

    // --- レイアウト参照 ---
    var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        modifier = modifier
            .pointerInput(Unit) {
                // --- タップ判定 ---
                detectTapGestures(
                    onPress = { offset ->
                        val hit = headerLayout?.let { layout ->
                            findHeaderHit(headerText = headerText, layout = layout, offset = offset)
                        }
                        val part = hit?.part
                        if (part != null) {
                            handleHeaderPartPress(
                                scope = scope,
                                part = part,
                                onPressedHeaderPartChange = onPressedHeaderPartChange,
                                awaitRelease = { awaitRelease() }
                            )
                        } else {
                            // タップ対象外は本文押下扱いにする。
                            handlePressFeedback(
                                scope = scope,
                                onFeedbackStart = { onContentPressedChange(true) },
                                onFeedbackEnd = { onContentPressedChange(false) },
                                awaitRelease = { awaitRelease() }
                            )
                        }
                    },
                    onTap = { offset ->
                        val hit = headerLayout?.let { layout ->
                            findHeaderHit(headerText = headerText, layout = layout, offset = offset)
                        }
                        if (hit?.part == PostHeaderPart.Id) {
                            onIdClick.invoke(id)
                        }
                    },
                    onLongPress = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val hit = headerLayout?.let { layout ->
                            findHeaderHit(headerText = headerText, layout = layout, offset = offset)
                        }
                        val part = hit?.part
                        if (part != null) {
                            onShowTextMenu(
                                hit.text.orEmpty(),
                                part.toNgType()
                            )
                        } else {
                            // 長押し対象外は投稿メニューを表示する。
                            onRequestMenu()
                        }
                    }
                )
            },
        // --- テキスト描画 ---
        text = headerText,
        style = headerTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = lineHeightEm.em,
        onTextLayout = { headerLayout = it }
    )
}

/**
 * タップ位置から該当するヘッダー部位を検出する。
 *
 * 注釈タグに一致した最初の部位を返す。
 */
private fun findHeaderHit(
    headerText: AnnotatedString,
    layout: TextLayoutResult,
    offset: Offset,
): HeaderHit {
    val pos = layout.getOffsetForPosition(offset)
    return PostHeaderPart.entries
        .firstNotNullOfOrNull { part ->
            headerText.getStringAnnotations(part.tag, pos, pos).firstOrNull()
                ?.let { HeaderHit(part, it.item) }
        }
        ?: HeaderHit(null, null)
}

/**
 * 押下中の部位を保持し、リリース時に解除する。
 */
private suspend fun handleHeaderPartPress(
    scope: CoroutineScope,
    part: PostHeaderPart,
    onPressedHeaderPartChange: (PostHeaderPart?) -> Unit,
    awaitRelease: suspend () -> Unit,
) {
    handlePressFeedback(
        scope = scope,
        feedbackDelayMillis = 0L,
        onFeedbackStart = { onPressedHeaderPartChange(part) },
        onFeedbackEnd = { onPressedHeaderPartChange(null) },
        awaitRelease = awaitRelease
    )
}

/**
 * ヘッダー部位に対応するNG種別を返す。
 */
private fun PostHeaderPart.toNgType(): NgType {
    return when (this) {
        PostHeaderPart.Name -> NgType.USER_NAME
        PostHeaderPart.Id -> NgType.USER_ID
    }
}

/**
 * ヘッダー表示用の AnnotatedString を構築する。
 *
 * 名前/メール+日付/ID/BEランクを順に連結し、タップ可能部位に注釈を付与する。
 */
private fun buildHeaderText(
    uiModel: PostHeaderUiModel,
    pressedHeaderPart: PostHeaderPart?,
    colors: PostHeaderTextColors,
): AnnotatedString {
    return buildAnnotatedString {
        // --- 連結ルール ---
        var first = true
        fun appendSpaceIfNeeded() {
            if (!first) append(" ") else first = false
        }

        // --- 追加ルール ---
        fun appendSegment(
            text: String,
            part: PostHeaderPart?,
            color: Color,
            isPressed: Boolean,
            annotationText: String = text,
        ) {
            appendSpaceIfNeeded()
            part?.let { partValue ->
                pushStringAnnotation(tag = partValue.tag, annotation = annotationText)
            }
            withStyle(
                SpanStyle(
                    color = if (isPressed) colors.pressed else color,
                    textDecoration = if (isPressed) TextDecoration.Underline else TextDecoration.None
                )
            ) {
                append(text)
            }
            part?.let { pop() }
        }

        val displayDate = formatDisplayDate(uiModel.header.date)
        val emailDate = buildEmailDate(email = uiModel.header.email, displayDate = displayDate)

        // --- 名前 ---
        if (uiModel.header.name.isNotBlank()) {
            appendSegment(
                text = uiModel.header.name,
                part = PostHeaderPart.Name,
                color = colors.onSurfaceVariant,
                isPressed = pressedHeaderPart == PostHeaderPart.Name
            )
        }

        // --- メールと日付 ---
        if (emailDate.isNotBlank()) {
            appendSegment(
                text = emailDate,
                part = null,
                color = colors.onSurfaceVariant,
                isPressed = false
            )
        }

        // --- ID ---
        if (uiModel.header.id.isNotBlank()) {
            appendSegment(
                text = uiModel.idText,
                part = PostHeaderPart.Id,
                color = colors.userId,
                isPressed = pressedHeaderPart == PostHeaderPart.Id,
                annotationText = uiModel.header.id
            )
        }

        // --- BEランク ---
        if (uiModel.header.beRank.isNotBlank()) {
            appendSegment(
                text = uiModel.header.beRank,
                part = null,
                color = colors.onSurfaceVariant,
                isPressed = false
            )
        }
    }
}
/**
 * 投稿日時の表示用テキストを整形する。
 *
 * 今年の年月日を示す接頭辞は省略する。
 */
private fun formatDisplayDate(date: String): String {
    val currentYearPrefix = currentYearPrefix()
    // 今年の接頭辞は本文表示から省略する。
    return if (date.startsWith(currentYearPrefix)) {
        date.removePrefix(currentYearPrefix)
    } else {
        date
    }
}

/**
 * メール欄と日付を結合した表示文字列を作成する。
 *
 * 空文字は除外し、スペース区切りで連結する。
 */
private fun buildEmailDate(email: String, displayDate: String): String {
    return listOf(email, displayDate).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * 表示簡略化に使う当年の接頭辞を返す。
 *
 * 例: "2025/" の形式。
 */
private fun currentYearPrefix(): String {
    val year = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDate.now().year
    } else {
        Calendar.getInstance().get(Calendar.YEAR)
    }
    return "$year/"
}

/**
 * 投稿ヘッダーのプレビューを表示する。
 */
@Preview(showBackground = true)
@Composable
private fun PostItemHeaderPreview() {
    val scope = rememberCoroutineScope()
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
        onPressedHeaderPartChange = { },
        onContentPressedChange = {},
        onRequestMenu = {},
        onReplyFromClick = {},
        onIdClick = {},
        onShowTextMenu = { _, _ -> },
    )
}
