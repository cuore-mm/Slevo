package com.websarva.wings.android.slevo.ui.util

import android.content.Context

fun isThreeButtonNavigation(context: Context): Boolean {
    val id = context.resources.getIdentifier(
        "config_navBarInteractionMode",
        "integer",
        "android"
    )
    return id > 0 && context.resources.getInteger(id) == 0
}
