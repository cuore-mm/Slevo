package com.websarva.wings.android.bbsviewer.data.util

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
            val content = cleanContent(parts[3])

            // 最初の行の場合、スレッドタイトルがある可能性がある
            if (index == 0) {
                threadTitle = parts[4].takeIf { it.isNotBlank() }
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
private fun cleanName(name: String): String {
    return name.replace("<b>", "")
        .replace("</b>", "")
        .replace("<small>", "")
        .replace("</small>", "")
}

private fun extractId(dateAndId: String): String? {
    // ID:xxx形式の抽出を試みる
    val idMatch = Regex("ID:([^\\s]+)").find(dateAndId)
    return idMatch?.groupValues?.get(1)
}

// content内の <br> を改行に置き換える関数
private fun cleanContent(content: String): String {
    // <br> タグを改行に置き換え
    var result =
        if (content.contains(" <br> ")) {
            content.replace(" <br> ", "\n")
        } else {
            content.replace("<br>", "\n")
        }

    // 最初と最後のスペースを削除
    result = result.substring(1)
    result = result.substring(0, result.length - 1)
    return result
}
