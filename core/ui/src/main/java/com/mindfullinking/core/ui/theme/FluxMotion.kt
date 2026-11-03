package com.mindfullinking.core.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class FluxMotion(
    val durationFastMs: Int = 100,
    val durationBaseMs: Int = 200,
    val durationRevealMs: Int = 350,
    val easingStandard: Easing = FastOutSlowInEasing,
    val easingDirect: Easing = LinearEasing
) {
    fun <T> fastSpec() = tween<T>(durationMillis = durationFastMs, easing = easingStandard)
    fun <T> baseSpec() = tween<T>(durationMillis = durationBaseMs, easing = easingStandard)
    fun <T> revealSpec() = tween<T>(durationMillis = durationRevealMs, easing = easingStandard)
}

val LocalFluxMotion = staticCompositionLocalOf { FluxMotion() }
