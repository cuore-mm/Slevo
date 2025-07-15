package com.websarva.wings.android.bbsviewer.data.util

import androidx.core.text.HtmlCompat
import com.websarva.wings.android.bbsviewer.ui.thread.ReplyInfo

fun parseDat(datContent: String): Pair<List<ReplyInfo>, String?> {
    // <>で分割する単純な方法を使用
    val replies = mutableListOf<ReplyInfo>()
    var threadTitle: String? = null

    datContent.split("\n").forEachIndexed { index, line ->
        // 空行はスキップ
        if (line.isBlank()) return@forEachIndexed

        val parts = line.split("<>")
        if (parts.size >= 5) {
            val name = cleanName(parts[0])
            val email = parts[1]
            // 日付とIDの部分（フォーマットが異なる場合もあるため正規表現に依存しない）
            val dateAndId = parts[2]
            // IDを抽出する（様々なフォーマットに対応）
            val id = extractId(dateAndId)
            val beInfo = extractBeInfo(dateAndId)
            val contentHtml = parts[3] // HTMLとして取得
            val (content, beIconUrl) = cleanContent(contentHtml) // HTMLデコードと<br>変換

            // 最初の行の場合、スレッドタイトルがある可能性がある
            if (index == 0) {
                // タイトルもHTMLデコード
                threadTitle =
                    HtmlCompat.fromHtml(parts[4], HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                        .takeIf { it.isNotBlank() }
            }

            replies.add(
                ReplyInfo(
                    name = name,
                    email = email,
                    date = dateAndId
                        .replace(Regex("\\s+ID:[^\\s]+"), "")
                        .replace(Regex("\\s+BE:[^\\s]+"), "")
                        .trim(),
                    id = id ?: "",
                    beLoginId = beInfo?.first ?: "",
                    beRank = beInfo?.second ?: "",
                    beIconUrl = beIconUrl,
                    content = content
                )
            )
        }
    }
    return Pair(replies, threadTitle)
}

// 不要なHTMLタグを除去する関数
private fun cleanName(nameHtml: String): String {
    // 名前フィールドもHTMLデコードする
    val name = HtmlCompat.fromHtml(nameHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    return name.replace("<b>", "") // fromHtmlで処理されない特定のタグは手動で除去することも検討
        .replace("</b>", "")
        .replace("<small>", "")
        .replace("</small>", "")
}

private fun extractId(dateAndId: String): String? {
    // ID:xxx形式の抽出を試みる
    val idMatch = Regex("ID:([^\\s]+)").find(dateAndId)
    return idMatch?.groupValues?.get(1)
}

private fun extractBeInfo(dateAndId: String): Pair<String, String>? {
    val match = Regex("BE:(\\d+)-([^\\s]+)").find(dateAndId)
    return match?.let { it.groupValues[1] to it.groupValues[2] }
}

// content内の <br> を改行に置き換え、HTMLエンティティをデコードする関数
private fun cleanContent(contentHtml: String): Pair<String, String> {
    // ユニークなプレースホルダを定義
    val newlinePlaceholder = "[[[NEWLINE_PLACEHOLDER]]]"

    var beIconUrl: String? = null

    // 1. <br> タグとBEアイコンを処理
    val removedIcon = contentHtml.replace(
        Regex("<img[^>]*src=\"(sssp://[^\"]+)\"[^>]*>", RegexOption.IGNORE_CASE)
    ) { matchResult ->
        beIconUrl = matchResult.groupValues[1].replace("sssp://", "http://")
        ""
    }
    val textWithPlaceholders =
        removedIcon.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), newlinePlaceholder)

    // 2. HTMLエンティティをデコード (この時プレースホルダはそのままのはず)
    val decodedContent =
        HtmlCompat.fromHtml(textWithPlaceholders, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    // 3. プレースホルダを実際の改行コード \n に戻す
    var finalContent = decodedContent.replace(newlinePlaceholder, "\n")
    finalContent = finalContent.replace("sssp://", "http://")

    // 4. 必要最小限のトリミング (主にfromHtmlが追加する可能性のある先頭/末尾の不要な空白)
    //    改行コード自体を消さないように注意
    finalContent = finalContent.trim() // Javaの Character.isWhitespace と同様の挙動

    return Pair(finalContent, beIconUrl ?: "")
}
