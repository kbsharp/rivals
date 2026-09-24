package com.kevinbevan.rivals.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.ui.theme.Motion
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.Space
import kotlin.math.roundToInt

/**
 * The scoreboard parts. "Every screen is anchored by a scoreboard": the same white numbers and
 * coloured names appear on Home, on the rivalry screen, over a session's history, and on the
 * board itself.
 */

/**
 * The head-to-head: two white numbers with a `hairline` dash between them, the players named
 * underneath in their own colours. [spread] pushes each number out over its player's name.
 */
@Composable
fun HeadToHead(
    yourScore: Int,
    rivalScore: Int,
    yourName: String,
    rivalName: String,
    modifier: Modifier = Modifier,
    numberStyle: TextStyle? = null,
    names: Boolean = true,
    spread: Boolean = false,
) {
    val score = numberStyle ?: Rivals.type.display
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s8)) {
        Row(
            // Centred, not on the baseline: with the score's line box trimmed to its digits, a
            // baseline-aligned dash hangs below them.
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(14.dp),
            modifier = (if (spread) Modifier.fillMaxWidth() else Modifier).semantics {
                contentDescription = "$yourName $yourScore, $rivalName $rivalScore"
            },
        ) {
            Text("$yourScore", style = score, color = Rivals.colors.fg)
            Text(
                "–",
                style = score.copy(fontSize = score.fontSize * 0.42f, fontWeight = FontWeight.Bold),
                color = Rivals.colors.hairline,
            )
            Text("$rivalScore", style = score, color = Rivals.colors.fg)
        }
        if (names) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(yourName, color = Rivals.colors.you)
                Label(rivalName, color = Rivals.colors.rival)
            }
        }
    }
}

/** Race pips: filled in the player's colour for frames won, a `hairline` ring for the rest. */
@Composable
fun Pips(
    won: Int,
    raceTo: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 14.dp,
) {
    if (raceTo == null || raceTo <= 0) return
    val ring = Rivals.colors.hairline
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Space.s8),
    ) {
        repeat(raceTo) { index ->
            if (index < won) {
                Box(Modifier.size(dotSize).background(color, CircleShape))
            } else {
                Box(
                    Modifier.size(dotSize).drawBehind {
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            color = ring,
                            radius = (this.size.minDimension - stroke) / 2,
                            style = Stroke(width = stroke),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Recent form as a colour bar: one segment per result, in the winner's colour, `hairline` for a
 * draw. It reads as texture until you look at it, which is what it is for.
 */
@Composable
fun FormBar(
    results: List<Boolean?>,
    modifier: Modifier = Modifier,
    yourName: String = "You",
    rivalName: String = "your rival",
) {
    if (results.isEmpty()) return
    val wins = results.count { it == true }
    val losses = results.count { it == false }
    val drawn = results.size - wins - losses
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics {
                contentDescription = buildString {
                    append("Last ${results.size}: $yourName won $wins, $rivalName won $losses")
                    if (drawn > 0) append(", $drawn drawn")
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        results.forEach { youWon ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        when (youWon) {
                            true -> Rivals.colors.you
                            false -> Rivals.colors.rival
                            null -> Rivals.colors.hairline
                        },
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

/** The form bar before any night has been played: [slots] empty `raised` segments. */
@Composable
fun EmptyFormBar(modifier: Modifier = Modifier, slots: Int = 10) {
    Row(
        modifier = modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        repeat(slots) {
            Box(Modifier.weight(1f).fillMaxSize().background(Rivals.colors.raised, RoundedCornerShape(4.dp)))
        }
    }
}

/**
 * The win-percentage ring: your share in teal, theirs in apricot, on a `raised` track, with the
 * percentage in white at the centre.
 */
@Composable
fun WinRing(
    yourWins: Int,
    rivalWins: Int,
    modifier: Modifier = Modifier,
    size: Dp = 152.dp,
) {
    val total = yourWins + rivalWins
    val share = if (total == 0) 0f else yourWins.toFloat() / total
    val animated by animateFloatAsState(share, Motion.tween(Motion.SLOW), label = "winShare")
    val track = Rivals.colors.raised
    val you = Rivals.colors.you
    val rival = Rivals.colors.rival
    val percent = (share * 100).roundToInt()
    Box(
        modifier = modifier.size(size).semantics {
            contentDescription =
                if (total == 0) "No matches played yet" else "You win $percent per cent of matches"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (total > 0) {
                val yourSweep = animated * 360f
                // A 2° gap at each end keeps the two round caps from touching.
                if (animated < 1f) {
                    drawArc(
                        color = rival,
                        startAngle = -90f + yourSweep + 2f,
                        sweepAngle = 360f - yourSweep - 4f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                if (animated > 0f) {
                    drawArc(
                        color = you,
                        startAngle = -90f + 2f,
                        sweepAngle = yourSweep - if (animated < 1f) 4f else 0f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (total == 0) "—" else "$percent%",
                style = Rivals.type.headline.copy(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
                color = Rivals.colors.fg,
            )
            Label("You win", color = Rivals.colors.fg3)
        }
    }
}

/**
 * The two tabs on the rivalry screen. The selected one is white with a 2dp white underline;
 * the other is `fg-3`. No container, no divider.
 */
@Composable
fun Tabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The underline slides between the tabs; the labels fade between fg and fg-3.
    val indicator by animateFloatAsState(
        selectedIndex.toFloat(),
        Motion.tween(Motion.FAST),
        label = "tab",
    )
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    if (selected) Rivals.colors.fg else Rivals.colors.fg3,
                    Motion.tween(Motion.FAST),
                    label = "tabLabel",
                )
                Box(
                    Modifier
                        .height(Space.touch)
                        .clickable(role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Label(title, color = color)
                }
            }
            Box(Modifier.height(2.dp).background(Rivals.colors.fg))
        },
    ) { measurables, constraints ->
        val count = titles.size
        val tabWidth = constraints.maxWidth / count
        val tabs = measurables.dropLast(1).map {
            it.measure(constraints.copy(minWidth = tabWidth, maxWidth = tabWidth))
        }
        val height = tabs.maxOfOrNull { it.height } ?: 0
        val underline = measurables.last().measure(
            constraints.copy(minWidth = tabWidth, maxWidth = tabWidth),
        )
        layout(constraints.maxWidth, height) {
            tabs.forEachIndexed { index, tab -> tab.place(index * tabWidth, 0) }
            underline.place((indicator * tabWidth).toInt(), height - underline.height)
        }
    }
}

/**
 * A mirrored stat row: your value, the label, theirs. The leader's number is white and heavy,
 * the other `fg-3` — so the table can be read down the middle without a line of colour in it.
 */
@Composable
fun StatRow(
    label: String,
    yourValue: String,
    rivalValue: String,
    modifier: Modifier = Modifier,
    yourLead: Boolean? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = "$label: you $yourValue, them $rivalValue"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatValue(yourValue, leading = yourLead == true, align = TextAlign.Start, width = 72.dp)
        Label(
            label,
            color = Rivals.colors.fg3,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StatValue(rivalValue, leading = yourLead == false, align = TextAlign.End, width = 72.dp)
    }
}

@Composable
private fun StatValue(value: String, leading: Boolean, align: TextAlign, width: Dp) {
    Text(
        value,
        style = Rivals.type.number.copy(
            fontWeight = if (leading) FontWeight.ExtraBold else FontWeight.SemiBold,
        ),
        color = if (leading) Rivals.colors.fg else Rivals.colors.fg3,
        textAlign = align,
        modifier = Modifier.width(width),
    )
}

/** The top line of a screen: an optional back arrow, the title, an optional overflow. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(Space.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(R.drawable.ic_arrow_back, "Back", onBack)
        }
        if (leading != null) {
            Box(Modifier.padding(start = Space.s4)) { leading() }
        }
        Text(
            title,
            style = Rivals.type.headline.copy(fontSize = 22.sp),
            color = Rivals.colors.fg,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null && leading == null) Space.s4 else Space.s8),
        )
        trailing?.invoke()
    }
}

/**
 * The logo, small: the 9-ball diamond rack, you at the top, your rival at the bottom and the 9
 * between them, drawn in the theme's colours so it sits in a top bar.
 */
@Composable
fun RackMark(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val colors = Rivals.colors
    Canvas(modifier.size(width = height * (RACK_WIDTH / RACK_HEIGHT), height = height).clearAndSetSemantics {}) {
        val unit = size.height / RACK_HEIGHT
        RACK.forEachIndexed { row, xs ->
            xs.forEach { x ->
                val colour = when {
                    row == 0 -> colors.you
                    row == RACK.lastIndex -> colors.rival
                    x == 0f -> colors.fg
                    else -> colors.hairline
                }
                drawCircle(
                    colour,
                    radius = BALL * unit,
                    center = Offset(size.width / 2 + x * unit, (BALL + row * ROW_GAP) * unit),
                )
            }
        }
    }
}

/** The rack's geometry, in the launcher icon's units: ball radius, row pitch, each row's x. */
private const val BALL = 6f
private const val ROW_GAP = 11.2f
private const val RACK_WIDTH = 37.6f
private const val RACK_HEIGHT = 56.8f
private val RACK = listOf(
    listOf(0f),
    listOf(-6.4f, 6.4f),
    listOf(-12.8f, 0f, 12.8f),
    listOf(-6.4f, 6.4f),
    listOf(0f),
)

/** A screen: the charcoal ground and the 16dp gutter. */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    gutter: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Rivals.colors.base)
            .padding(horizontal = if (gutter) Space.gutter else 0.dp),
        content = content,
    )
}
