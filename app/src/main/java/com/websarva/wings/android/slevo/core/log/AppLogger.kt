package com.websarva.wings.android.slevo.core.log

/**
 * アプリケーション内のログ出力を抽象化するインターフェース。
 *
 * ログライブラリ（Kermit など）を直接参照せず、
 * Kotlin 標準型のみを用いた API でログを出力できる。
 * 将来 KMP の shared module へ移行する際も、
 * 呼び出し側の変更を最小限に抑えられる。
 */
interface AppLogger {
    /**
     * DEBUG レベルのログを出力する。
     */
    fun d(message: String, tag: String? = null, throwable: Throwable? = null)

    /**
     * INFO レベルのログを出力する。
     */
    fun i(message: String, tag: String? = null, throwable: Throwable? = null)

    /**
     * ERROR レベルのログを出力する。
     */
    fun e(message: String, tag: String? = null, throwable: Throwable? = null)
}
