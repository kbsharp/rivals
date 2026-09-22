package com.kevinbevan.rivals.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two players' identity colours: "me" (whoever holds the phone) and the rival. Used for
 * score buttons, frame dots and stats bars, always next to the player's name, never alone.
 *
 * Validated with the dataviz palette checker (lightness band, chroma, colour-blind and
 * normal-vision separation, 3:1 against the surface), separately for each theme:
 * light #1B8A5A / #3B6FD8 (CVD ΔE 21.7), dark #2E9E6B / #5B87E3 (CVD ΔE 19.0).
 */
@Immutable
data class PlayerColors(val me: Color, val onMe: Color, val rival: Color, val onRival: Color)

internal val LightPlayerColors = PlayerColors(
    me = Color(0xFF1B8A5A),
    onMe = Color.White,
    rival = Color(0xFF3B6FD8),
    onRival = Color.White,
)

internal val DarkPlayerColors = PlayerColors(
    me = Color(0xFF2E9E6B),
    onMe = Color(0xFF04140C),
    rival = Color(0xFF5B87E3),
    onRival = Color(0xFF040C1F),
)

val LocalPlayerColors = staticCompositionLocalOf { LightPlayerColors }
