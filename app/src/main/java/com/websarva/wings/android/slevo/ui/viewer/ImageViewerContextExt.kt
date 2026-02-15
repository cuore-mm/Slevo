package com.websarva.wings.android.slevo.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Context から Activity を辿って返す。
 *
 * Activity でない Context の場合は ContextWrapper をたどり、見つからないときは null を返す。
 */
internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
