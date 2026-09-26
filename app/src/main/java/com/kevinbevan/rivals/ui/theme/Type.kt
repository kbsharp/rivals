package com.kevinbevan.rivals.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kevinbevan.rivals.R

/** Montserrat, bundled: every number and every screen title. */
val Montserrat = FontFamily(
    Font(R.font.montserrat_600, FontWeight.SemiBold),
    Font(R.font.montserrat_700, FontWeight.Bold),
    Font(R.font.montserrat_800, FontWeight.ExtraBold),
)

/** Barlow, bundled: all other text. */
val Barlow = FontFamily(
    Font(R.font.barlow_400, FontWeight.Normal),
    Font(R.font.barlow_500, FontWeight.Medium),
    Font(R.font.barlow_600, FontWeight.SemiBold),
    Font(R.font.barlow_700, FontWeight.Bold),
)

/** Tabular figures, so a score never shifts as it ticks. */
private const val Tabular = "tnum"

/**
 * Line heights are exactly what the brief says, so a number sits where it was drawn: trim the
 * first line's ascent padding and centre the box on the glyphs.
 */
private val Centred = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** For a number that stands on its own: the box hugs the glyphs, with no leading around them. */
private val Trimmed = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun montserrat(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = Montserrat,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = Tabular,
    lineHeightStyle = Centred,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun barlow(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = Barlow,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = Tabular,
    lineHeightStyle = Centred,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * The type scale from `design/brief.md`. `score` is sized at the call site (about 45% of the
 * half's height on the scoreboard), so it is a base style rather than a fixed size.
 */
@Immutable
data class RivalsTypography(
    /** The scoreboard numbers. Size it where it is used. */
    val score: TextStyle = montserrat(96, 96, FontWeight.ExtraBold).copy(lineHeightStyle = Trimmed),
    /** Head-to-head on Home. */
    val display: TextStyle = montserrat(56, 60, FontWeight.ExtraBold).copy(lineHeightStyle = Trimmed),
    /** Screen titles, the match-won line. */
    val headline: TextStyle = montserrat(24, 30, FontWeight.Bold),
    /** A number inside a row: a session's score, a stat's value. */
    val number: TextStyle = montserrat(22, 26, FontWeight.ExtraBold),
    /** Row titles, card headings. */
    val title: TextStyle = barlow(20, 26, FontWeight.SemiBold),
    /** Sentences. */
    val body: TextStyle = barlow(16, 22, FontWeight.Normal),
    /** A row's own line: the name it leads with. */
    val rowTitle: TextStyle = barlow(16, 22, FontWeight.SemiBold),
    /** The quieter second line of a row. */
    val caption: TextStyle = barlow(13, 18, FontWeight.Normal),
    /** Everything above a value. Uppercase it at the call site. */
    val label: TextStyle = barlow(12, 16, FontWeight.SemiBold).copy(letterSpacing = 0.12.em),
    /** A label that has to fit somewhere tight, inside a ring or beside a score. */
    val labelSmall: TextStyle = barlow(11, 14, FontWeight.SemiBold).copy(letterSpacing = 0.12.em),
    /** The text inside a button. */
    val button: TextStyle = barlow(16, 22, FontWeight.Bold),
    /**
     * The scoreboard's status line, read from the rail: the one size outside the steps above
     * (brief, "Where actions live"). Uppercase it at the call site.
     */
    val status: TextStyle = barlow(14, 20, FontWeight.SemiBold).copy(letterSpacing = 0.06.em),
)

val LocalRivalsTypography = androidx.compose.runtime.staticCompositionLocalOf { RivalsTypography() }

/**
 * The Material 3 roles, filled from the same scale, so anything still drawn with a stock
 * component (a dialog, a menu) reads in the app's type rather than Roboto.
 */
internal fun materialTypography(type: RivalsTypography) = Typography(
    displayLarge = type.display,
    displayMedium = type.display,
    displaySmall = type.headline,
    headlineLarge = type.headline,
    headlineMedium = type.headline,
    headlineSmall = type.headline,
    titleLarge = type.title,
    titleMedium = type.rowTitle,
    titleSmall = barlow(14, 20, FontWeight.SemiBold),
    bodyLarge = type.body,
    bodyMedium = barlow(14, 20, FontWeight.Normal),
    bodySmall = type.caption,
    labelLarge = barlow(14, 20, FontWeight.SemiBold),
    labelMedium = type.label,
    labelSmall = type.labelSmall,
)
