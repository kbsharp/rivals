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
 * pips, their bar in a stat and the "you won" caption — never on a number. [live] is the only
 * red in the app: the live dot, and delete.
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
    base = Color(0xFF131418),
    surface = Color(0xFF1B1D23),
    raised = Color(0xFF262A33),
    hairline = Color(0xFF363B47),
    fg = Color(0xFFF4F5F7),
    fg2 = Color(0xFFA8AEBC),
    fg3 = Color(0xFF808898),
    you = Color(0xFF6FD3C4),
    youTint = Color(0x296FD3C4),
    rival = Color(0xFFF0A883),
    rivalTint = Color(0x29F0A883),
    live = Color(0xFFFF4757),
    liveTint = Color(0x29FF4757),
    onFg = Color(0xFF131418),
)

/**
 * Derived from the dark palette, not designed separately: the neutrals invert, and the two
 * player colours darken until they carry 4.5:1 on the light ground. Phase 12 revisits it.
 */
internal val LightRivalsColors = RivalsColors(
    base = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE8EAEF),
    hairline = Color(0xFFC9CDD8),
    fg = Color(0xFF15171C),
    fg2 = Color(0xFF515868),
    fg3 = Color(0xFF666E7E),
    you = Color(0xFF13756A),
    youTint = Color(0x2913756A),
    rival = Color(0xFF9A4F1E),
    rivalTint = Color(0x299A4F1E),
    live = Color(0xFFC81E2C),
    liveTint = Color(0x29C81E2C),
    onFg = Color(0xFFF7F8FA),
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

/** 8dp chips and score boxes, 16dp panels, fully round buttons. */
object Shapes {
    val chip = RoundedCornerShape(8.dp)
    val panel = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
}
