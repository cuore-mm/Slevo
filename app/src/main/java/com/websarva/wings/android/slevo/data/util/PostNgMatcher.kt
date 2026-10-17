package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.NgType

/** NG判定に必要なレスの識別情報を表し、取得層と表示層の判定入力を統一する。 */
data class PostNgTarget(
    val id: String,
    val name: String,
    val content: String,
)

/** コンパイル済みパターンと適用範囲を保持するレスNGルールを表す。 */
data class CompiledPostNgRule(
    val boardId: Long?,
    val regex: Regex,
    val type: NgType,
)

/** 投稿をID・名前・本文のNG規則で判定する共通matcher。 */
object PostNgMatcher {
    /** NG保存値をコンパイルし、無効な正規表現を既存画面と同様に除外する。 */
    fun compile(rules: List<NgEntity>): List<CompiledPostNgRule> = rules.mapNotNull { rule ->
        runCatching {
            val regex = if (rule.isRegex) Regex(rule.pattern) else Regex(Regex.escape(rule.pattern))
            CompiledPostNgRule(rule.boardId, regex, rule.type)
        }.getOrNull()
    }

    /** 指定板で対象レスがID・名前・本文のいずれかのNG規則に一致するか返す。 */
    fun isIgnored(
        post: PostNgTarget,
        boardId: Long,
        rules: List<CompiledPostNgRule>,
    ): Boolean = rules.any { rule ->
        (rule.boardId == null || rule.boardId == boardId) && runCatching {
            val value = when (rule.type) {
                NgType.USER_ID -> post.id
                NgType.USER_NAME -> post.name
                NgType.WORD -> post.content
                NgType.THREAD_TITLE -> return@any false
            }
            rule.regex.containsMatchIn(value)
        }.getOrDefault(false)
    }

    /** 投稿一覧からNGに一致する1始まりのレス番号を返す。 */
    fun ignoredPostNumbers(
        posts: List<PostNgTarget>,
        boardId: Long,
        rules: List<CompiledPostNgRule>,
    ): Set<Int> = posts.mapIndexedNotNull { index, post ->
        if (isIgnored(post, boardId, rules)) index + 1 else null
    }.toSet()
}
