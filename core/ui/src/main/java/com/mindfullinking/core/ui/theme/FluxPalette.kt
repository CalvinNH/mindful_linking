package com.mindfullinking.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Measured Bauhaus palette derived from reference design.
 * 
 * Foreground/Contrast Rules:
 * - Use Charcoal (Void #222124) text on white, gray, pale-lime, vivid-lime, and lavender fills.
 * - Use White (Paper #FFFFFF) text ONLY on Charcoal (Void #222124) fills.
 * - NEVER use white text on vivid lime (#DAF163) or lavender (#BBB2F5)!
 */
@Immutable
object FluxPalette {
    /** Structural charcoal: App shell, dark cards, primary text on light surfaces (#222124) */
    val Void = Color(0xFF222124)
    
    /** Paper white: Primary cards and high-emphasis surfaces (#FFFFFF) */
    val Paper = Color(0xFFFFFFFF)
    
    /** Workspace gray: Main content background and neutral containers (#E1E1E1) */
    val Surface = Color(0xFFE1E1E1)
    
    /** Environmental pale lime: Root canvas, branded empty states (#DCEA95) */
    val CanvasLime = Color(0xFFDCEA95)
    
    /** Action lime: Primary CTA, selected state, progress emphasis (#DAF163) */
    val Signal = Color(0xFFDAF163)
    
    /** Data lavender: Secondary metrics, comparison data, alternate progress (#BBB2F5) */
    val Lavender = Color(0xFFBBB2F5)
    
    /** Muted lavender: Disabled/secondary chart marks and low-emphasis data (#B1ADCC) */
    val LavenderMuted = Color(0xFFB1ADCC)
    
    /** Dark supporting gray: Secondary text on dark cards, inactive marks (#4C4C49) */
    val CharcoalMuted = Color(0xFF4C4C49)
}

/**
 * Semantic color roles enforcing accessibility and design integrity.
 */
@Immutable
data class FluxColors(
    val background: Color = FluxPalette.Surface,
    val canvas: Color = FluxPalette.CanvasLime,
    val surface: Color = FluxPalette.Paper,
    val surfaceInverted: Color = FluxPalette.Void,
    val contentPrimary: Color = FluxPalette.Void,
    val contentSecondary: Color = FluxPalette.CharcoalMuted,
    val contentOnInverted: Color = FluxPalette.Paper,
    val accentAction: Color = FluxPalette.Signal,
    val contentOnAction: Color = FluxPalette.Void,
    val accentData: Color = FluxPalette.Lavender,
    val contentOnData: Color = FluxPalette.Void,
    val accentDataMuted: Color = FluxPalette.LavenderMuted,
    val border: Color = Color(0x1F222124),
    val borderInverted: Color = Color(0x3FFFFFFF),
    val statusWarning: Color = FluxPalette.Signal,
    val statusDanger: Color = Color(0xFFD32F2F)
) {
    companion object {
        /**
         * Dark scheme keeps the measured Bauhaus accents while moving structural surfaces to
         * charcoal. Accent labels remain charcoal, never white, to preserve the contrast rule.
         */
        fun dark() = FluxColors(
            background = FluxPalette.Void,
            canvas = FluxPalette.CharcoalMuted,
            surface = FluxPalette.CharcoalMuted,
            // Structural charcoal stays charcoal in either scheme; only its foreground changes.
            surfaceInverted = FluxPalette.Void,
            contentPrimary = FluxPalette.Paper,
            contentSecondary = FluxPalette.Paper.copy(alpha = 0.76f),
            contentOnInverted = FluxPalette.Paper,
            accentAction = FluxPalette.Signal,
            contentOnAction = FluxPalette.Void,
            accentData = FluxPalette.Lavender,
            contentOnData = FluxPalette.Void,
            accentDataMuted = FluxPalette.LavenderMuted,
            border = FluxPalette.Paper.copy(alpha = 0.28f),
            borderInverted = FluxPalette.Void.copy(alpha = 0.28f),
            statusWarning = FluxPalette.Signal,
            statusDanger = Color(0xFFFF8A80)
        )
    }
}

val LocalFluxColors = staticCompositionLocalOf { FluxColors() }
