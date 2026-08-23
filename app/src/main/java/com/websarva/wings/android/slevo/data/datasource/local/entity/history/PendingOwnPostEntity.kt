package com.websarva.wings.android.slevo.data.datasource.local.entity.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * スレッド本文への反映を待っている自分の投稿を表すRoomエンティティ。
 *
 * 投稿成功時の入力値を保持し、取得後に一致したレス番号と既存投稿履歴を確定するまで
 * `PENDING` 状態で維持する。provider、板、スレッドの文字列キーで照合対象を限定する。
 */
@Entity(
    tableName = "pending_own_posts",
    indices = [
        Index(value = ["providerId", "boardKey", "threadKey", "status"]),
        Index(value = ["status", "submittedAt"]),
    ],
)
data class PendingOwnPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val boardKey: String,
    val threadKey: String,
    val status: String = PendingOwnPostStatus.PENDING.name,
    val content: String,
    val name: String,
    val email: String,
    val baseResCount: Int,
    val lastCheckedResNum: Int,
    val submittedAt: Long,
    val expiresAt: Long,
    val matchedResNum: Int? = null,
    val confirmedResNum: Int? = null,
    val serverPostDateMillis: Long? = null,
    val posterIdHint: String? = null,
)

/**
 * 未確定投稿の永続状態。
 *
 * `PENDING` は照合待ち、`MATCHED` は投稿履歴へ確定済み、`EXPIRED` は期限切れを表す。
 */
enum class PendingOwnPostStatus {
    PENDING,
    MATCHED,
    EXPIRED,
}
