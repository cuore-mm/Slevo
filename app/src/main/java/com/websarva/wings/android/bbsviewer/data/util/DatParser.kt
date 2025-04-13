package com.websarva.wings.android.bbsviewer.data.util

import com.websarva.wings.android.bbsviewer.ui.thread.ReplyInfo

fun parseDat(datContent: String): Pair<List<ReplyInfo>, String?> {
    // 各行は以下の形式とする
    // 最初の行: 名前<>E-mail<>日付とIDとBE<>本文<>スレッドタイトル
    // 以降の行: 名前<>E-mail<>日付とIDとBE<>本文<>
    // 正規表現：最初の5キャプチャは各レスの要素、6番目(任意)はスレッドタイトル
    val regex = Regex("^(.+?)<>(.*?)<>(.*?)\\s+ID:(\\w+)<>\\s(.*?)(?:<>(.*))?\$")
    val replies = mutableListOf<ReplyInfo>()
    var threadTitle: String? = null

    datContent.split("\n").forEachIndexed { index, line ->
        // 空行はスキップ
        if (line.isBlank()) return@forEachIndexed
        val match = regex.find(line)
        if (match != null) {
            // 最初の行の場合、6番目のキャプチャグループにスレッドタイトルが格納される
            if (index == 0 && match.groupValues.size >= 7) {
                // 空文字の場合はnullにしておく（必要に応じて）
                val title = match.groupValues[6]
                threadTitle = if (title.isNotBlank()) title else null
            }
            replies.add(
                ReplyInfo(
                    name = match.groupValues[1],
                    email = match.groupValues[2],
                    date = match.groupValues[3],
                    id = match.groupValues[4],
                    content = match.groupValues[5]
                )
            )
        }
    }
    return Pair(replies, threadTitle)
}
