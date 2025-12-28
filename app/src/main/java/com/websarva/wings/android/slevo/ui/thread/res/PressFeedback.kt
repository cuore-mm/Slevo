package com.websarva.wings.android.slevo.ui.thread.res

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PressFeedbackDelayMillis = 80L

suspend fun handlePressFeedback(
    scope: CoroutineScope,
    feedbackDelayMillis: Long = PressFeedbackDelayMillis,
    onFeedbackStart: () -> Unit,
    onFeedbackEnd: () -> Unit,
    awaitRelease: suspend () -> Unit
) {
    var job: Job? = null
    try {
        job = scope.launch {
            delay(feedbackDelayMillis)
            onFeedbackStart()
        }
        awaitRelease()
    } finally {
        job?.cancel()
        onFeedbackEnd()
    }
}
