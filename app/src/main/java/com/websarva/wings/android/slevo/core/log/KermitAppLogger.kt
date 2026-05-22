package com.websarva.wings.android.slevo.core.log

import co.touchlab.kermit.Logger
import javax.inject.Inject

/**
 * [AppLogger] の Kermit ベース実装。
 *
 * 呼び出し側は Kermit API を知らなくてもよく、
 * [AppLogger] 経由でログ出力を行う。
 * tag や Throwable の扱いはこの実装内で Kermit に委譲する。
 */
class KermitAppLogger @Inject constructor() : AppLogger {

    override fun d(message: String, tag: String?, throwable: Throwable?) {
        Logger.d(throwable = throwable, tag = tag ?: Logger.tag) { message }
    }

    override fun i(message: String, tag: String?, throwable: Throwable?) {
        Logger.i(throwable = throwable, tag = tag ?: Logger.tag) { message }
    }

    override fun e(message: String, tag: String?, throwable: Throwable?) {
        Logger.e(throwable = throwable, tag = tag ?: Logger.tag) { message }
    }
}
