package com.mindfullinking.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class FluxShapes(
    val cardPrimary: Shape = RoundedCornerShape(24.dp),
    val cardCompact: Shape = RoundedCornerShape(16.dp),
    val cardHero: Shape = RoundedCornerShape(32.dp),
    val pill: Shape = RoundedCornerShape(50),
    val button: Shape = RoundedCornerShape(50),
    val badge: Shape = RoundedCornerShape(50),
    val dialog: Shape = RoundedCornerShape(24.dp),
    val input: Shape = RoundedCornerShape(16.dp),
    val switchTrack: Shape = RoundedCornerShape(50),
    val switchThumb: Shape = RoundedCornerShape(50)
)

val LocalFluxShapes = staticCompositionLocalOf { FluxShapes() }
