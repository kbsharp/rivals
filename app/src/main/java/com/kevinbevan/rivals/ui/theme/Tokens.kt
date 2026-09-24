package com.kevinbevan.rivals.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The palette from `design/brief.md`. Dark is the design; light is derived from it.
 *
 * Scores are always [fg]. A player's colour ([you], [rival]) appears on their name, their race
 * pips, their bar in a stat and the "you won" caption — never on a number. [live] is amber,
 * not red, so it can't be mistaken for the rival's pink: the live dot, and delete.
 */
@Immutable
data class RivalsColors(
    val base: Color,
    val surface: Color,
    val raised: Color,
    val hairline: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val you: Color,
    val youTint: Color,
    val rival: Color,
    val rivalTint: Color,
    val live: Color,
    val liveTint: Color,
    /** Text on a [fg]-filled primary button. */
    val onFg: Color,
) {
    /** The colour that names [playerId], given the two players of a rivalry. */
    fun forPlayer(playerId: String?, youId: String?): Color =
        if (playerId != null && playerId == youId) you else rival

    fun tintForPlayer(playerId: String?, youId: String?): Color =
        if (playerId != null && playerId == youId) youTint else rivalTint
}

internal val DarkRivalsColors = RivalsColors(
    base = Color(0xFF0D0B14),
    surface = Color(0xFF18151F),
    raised = Color(0xFF231F2C),
    hairline = Color(0xFF332D40),
    fg = Color(0xFFF4F5F7),
    fg2 = Color(0xFFABA6BA),
    fg3 = Color(0xFF857F96),
    you = Color(0xFF3FE3EC),
    youTint = Color(0x293FE3EC),
    rival = Color(0xFFFF5FA8),
    rivalTint = Color(0x29FF5FA8),
    live = Color(0xFFFFB020),
    liveTint = Color(0x29FFB020),
    onFg = Color(0xFF0D0B14),
)

/**
 * Derived from the dark palette, not designed separately: the neutrals invert, and the two
 * player colours darken until they carry 4.5:1 on the light ground.
 */
internal val LightRivalsColors = RivalsColors(
    base = Color(0xFFF8F7FA),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFECEAF0),
    hairline = Color(0xFFCDC9D8),
    fg = Color(0xFF15131C),
    fg2 = Color(0xFF56516A),
    fg3 = Color(0xFF6A6480),
    you = Color(0xFF00788A),
    youTint = Color(0x2900788A),
    rival = Color(0xFFC2186A),
    rivalTint = Color(0x29C2186A),
    live = Color(0xFF9A5B00),
    liveTint = Color(0x299A5B00),
    onFg = Color(0xFFF8F7FA),
)

val LocalRivalsColors = staticCompositionLocalOf { DarkRivalsColors }

/** The 4dp grid from the brief. Screens sit inside [gutter]; sections are [section] apart. */
object Space {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s48 = 48.dp

    val gutter = 16.dp
    val section = 24.dp

    /** The smallest thing a thumb is asked to hit. */
    val touch = 48.dp
}

/**
 * Motion, from `design/brief.md`: 150–250ms and no bounce. Nothing in the app springs, and
 * nothing on the scoreboard moves except the score.
 */
object Motion {
    /** A state that should feel instant: a chip filling, a tab underline. */
    const val FAST = 150

    /** The usual: a score rolling, a panel arriving. */
    const val NORMAL = 200

    /** The longest anything takes: a ring redrawing itself. */
    const val SLOW = 250

    /** One easing for everything, so nothing overshoots. */
    val easing = androidx.compose.animation.core.FastOutSlowInEasing

    fun <T> tween(durationMillis: Int = NORMAL) =
        androidx.compose.animation.core.tween<T>(durationMillis, easing = easing)
}

/** 8dp chips and score boxes, 16dp panels, fully round buttons. */
object Shapes {
    val chip = RoundedCornerShape(8.dp)
    val panel = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
}
