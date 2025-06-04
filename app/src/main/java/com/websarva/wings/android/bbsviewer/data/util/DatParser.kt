package com.websarva.wings.android.bbsviewer.data.util

import android.util.Log
import androidx.core.text.HtmlCompat
import com.websarva.wings.android.bbsviewer.ui.thread.ReplyInfo

fun parseDat(datContent: String): Pair<List<ReplyInfo>, String?> {
    // <>で分割する単純な方法を使用
    val replies = mutableListOf<ReplyInfo>()
    var threadTitle: String? = null

    Log.i("DatParser", datContent)
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
    Log.i("DatParser", replies.toString())
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
    // ユニークなプレースホルダを定義
    val newlinePlaceholder = "[[[NEWLINE_PLACEHOLDER]]]"

    // 1. <br> タグをプレースホルダに変換
    val textWithPlaceholders = contentHtml.replace(Regex(" <br\\s*/?> ", RegexOption.IGNORE_CASE), newlinePlaceholder)
    Log.d("DatParserDebug", "textWithPlaceholders: [$textWithPlaceholders]")

    // 2. HTMLエンティティをデコード (この時プレースホルダはそのままのはず)
    val decodedContent = HtmlCompat.fromHtml(textWithPlaceholders, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    Log.d("DatParserDebug", "decodedContent (after fromHtml): [$decodedContent]")

    // 3. プレースホルダを実際の改行コード \n に戻す
    var finalContent = decodedContent.replace(newlinePlaceholder, "\n")
    Log.d("DatParserDebug", "finalContent (after placeholder replacement): [$finalContent]")

    // 4. 必要最小限のトリミング (主にfromHtmlが追加する可能性のある先頭/末尾の不要な空白)
    //    改行コード自体を消さないように注意
    finalContent = finalContent.trim { it <= ' ' } // Javaの Character.isWhitespace と同様の挙動

    // 例: fromHtml が末尾に余計な改行を1つだけ付ける場合で、それが不要な場合の処理
    // if (finalContent.endsWith("\n\n")) { // ユーザーが意図した改行 + fromHtmlの改行
    //     finalContent = finalContent.dropLast(1)
    // } else if (finalContent.endsWith("\n") && !contentHtml.endsWith("<br>") && !contentHtml.endsWith("<br/>")) {
    //     // 元のHTMLが<br>で終わっていないのに、変換後が\nで終わっている場合はfromHtmlが付与した可能性がある
    //     // ただし、この判定は完璧ではない
    // }


    Log.d("DatParserDebug", "Final returned content: [$finalContent]")
    return finalContent
}
