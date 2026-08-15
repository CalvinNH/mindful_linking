package com.mindfullinking.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class FluxSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    
    val screenGutterCompact: Dp = 16.dp,
    val screenGutterRegular: Dp = 20.dp,
    val screenGutterExpanded: Dp = 24.dp,
    
    val cardGapCompact: Dp = 12.dp,
    val cardGapRegular: Dp = 16.dp,
    
    val cardPaddingCompact: Dp = 16.dp,
    val cardPaddingRegular: Dp = 20.dp,
    val cardPaddingHero: Dp = 24.dp,
    
    val touchTargetMin: Dp = 48.dp
)

val LocalFluxSpacing = staticCompositionLocalOf { FluxSpacing() }
