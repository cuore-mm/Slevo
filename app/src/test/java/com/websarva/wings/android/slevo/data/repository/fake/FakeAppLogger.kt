package com.websarva.wings.android.slevo.data.repository.fake

import com.websarva.wings.android.slevo.core.log.AppLogger

/**
 * テスト用の `AppLogger` fake。
 *
 * ログメッセージを保持し、テストで検証できる。
 */
class FakeAppLogger : AppLogger {
    val debugMessages = mutableListOf<String>()
    val infoMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<Pair<String, Throwable?>>()

    override fun d(message: String, tag: String?, throwable: Throwable?) {
        debugMessages += message
    }

    override fun i(message: String, tag: String?, throwable: Throwable?) {
        infoMessages += message
    }

    override fun e(message: String, tag: String?, throwable: Throwable?) {
        errorMessages += message to throwable
    }
}
