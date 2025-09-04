package com.websarva.wings.android.slevo.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val DefaultAnimDuration = 450

// --- 通常画面用トランジション ---
fun defaultEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeIn(animationSpec = tween(DefaultAnimDuration))

fun defaultExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeOut(animationSpec = tween(DefaultAnimDuration))

fun defaultPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeIn(animationSpec = tween(DefaultAnimDuration))

fun defaultPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(DefaultAnimDuration)
    ) + fadeOut(animationSpec = tween(DefaultAnimDuration))
