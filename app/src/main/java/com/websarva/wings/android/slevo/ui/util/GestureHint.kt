package com.websarva.wings.android.slevo.ui.util

import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection

sealed interface GestureHint {
    data object Hidden : GestureHint

    data class Direction(
        val direction: GestureDirection,
        val action: GestureAction?,
    ) : GestureHint

    data object Invalid : GestureHint
}
