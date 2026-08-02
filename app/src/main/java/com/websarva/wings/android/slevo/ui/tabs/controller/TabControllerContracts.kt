package com.websarva.wings.android.slevo.ui.tabs.controller

import com.websarva.wings.android.slevo.ui.bbsroute.TabPresentationState

/**
 * タブ Controller が Room の初回スナップショットを受け取ったかどうかを表す。
 * Loading 中は一覧を空として確定せず、Loaded になってから通常 command を受理する。
 */
enum class TabLoadPhase {
    Loading,
    Loaded,
}

/**
 * 受理したタブ command を識別する一意な ID。
 * Controller の生存期間内で重複せず、terminal result の配信単位にも使う。
 */
@JvmInline
value class TabCommandId(val value: Long)

/**
 * command が Controller 内でたどる状態を表すライフサイクル語彙。
 * caller の待機 Job とは独立して Controller が保持する。
 */
enum class TabCommandLifecycle {
    Accepted,
    CommittedAwaitingCanonical,
    Confirmed,
    Failed,
}

/**
 * Controller command の明示的な終端結果。
 * Success、NoOp、Failure を区別し、presentation の観測を結果の代用にしない。
 */
sealed interface TabCommandResult<out Value> {
    /** Repository write と canonical reconciliation が完了した結果。 */
    data class Success<Value>(val value: Value) : TabCommandResult<Value>

    /** 変更はなかったが repository が失敗していない結果。 */
    data class NoOp<Value>(val value: Value? = null) : TabCommandResult<Value>

    /** Repository または Controller teardown による終端失敗。 */
    data class Failure(val cause: Throwable) : TabCommandResult<Nothing>
}

/**
 * Board／Thread Controller が共有する immutable state の形。
 * domain 固有の pending payload は型引数で保持し、Controller 自体は分離したまま使う。
 */
data class TabControllerState<Tab : Any, Key : Any, Pending : Any>(
    val loadPhase: TabLoadPhase,
    val canonicalTabs: List<Tab>,
    val pendingCommands: List<Pending>,
    val selectedKey: Key?,
    val presentation: TabPresentationState<Tab, Key>,
)
