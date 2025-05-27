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
            val contentHtml = parts[3] // HTMLとして取得
            val content = cleanContent(contentHtml) // HTMLデコードと<br>変換

            // 最初の行の場合、スレッドタイトルがある可能性がある
            if (index == 0) {
                // タイトルもHTMLデコード
                threadTitle = HtmlCompat.fromHtml(parts[4], HtmlCompat.FROM_HTML_MODE_LEGACY).toString().takeIf { it.isNotBlank() }
            }

            replies.add(
                ReplyInfo(
                    name = name,
                    email = email,
                    date = dateAndId.replace(Regex("\\s+ID:.*$"), "").trim(), // IDの部分を除去
                    id = id ?: "???", // ID抽出に失敗した場合のデフォルト値
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

// content内の <br> を改行に置き換え、HTMLエンティティをデコードする関数
private fun cleanContent(contentHtml: String): String {
    // 1. <br> タグを改行コードに変換
    var resultWithNewlines =
        if (contentHtml.contains(" <br> ")) {
            contentHtml.replace(" <br> ", "\n")
        } else {
            contentHtml.replace("<br>", "\n")
        }

    // 2. HTMLエンティティをデコード
    var decodedContent = HtmlCompat.fromHtml(resultWithNewlines, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    // 3. 最初と最後の不要な空白や改行を削除 (fromHtmlの結果によって調整が必要な場合がある)
    decodedContent = decodedContent.trim() // 一般的なtrim

    // DATファイルの特性上、最初のスペースや最後の改行が入ることがあるため、より具体的に処理
    // (例: " 本文 " のような形式から "本文" へ)
    if (decodedContent.startsWith(" ") && decodedContent.length > 1) {
        decodedContent = decodedContent.substring(1)
    }
    // fromHtmlが最後に余計な改行を付与する場合があるため、それも除去
    if (decodedContent.endsWith("\n")) {
        decodedContent = decodedContent.dropLast(1)
    }
    return decodedContent
}
