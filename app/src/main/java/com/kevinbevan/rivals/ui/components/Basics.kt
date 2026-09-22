package com.kevinbevan.rivals.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space

/**
 * The parts every screen is built from, straight out of `design/brief.md`. Nothing here
 * introduces a colour, size or radius that isn't in the brief, and nothing draws a divider.
 */

/** A small uppercase label. Always sits above the value it names. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Rivals.colors.fg3,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        style = Rivals.type.label,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** The screen's one primary action: a white pill with charcoal text. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(Rivals.colors.fg, Shapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Rivals.type.button, color = Rivals.colors.onFg)
    }
}

/** Anything that isn't the primary action: a `raised` pill, never coloured. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(Rivals.colors.raised, Shapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Rivals.type.button.copy(fontSize = Rivals.type.button.fontSize * 0.94f),
            color = if (destructive) Rivals.colors.live else Rivals.colors.fg,
        )
    }
}

/** A text-only action, for the second-choice link under a primary button. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Rivals.colors.fg2,
) {
    Box(
        modifier = modifier
            .heightIn(min = Space.touch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s8),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Rivals.type.body, color = color)
    }
}

/** A 48dp icon button: the only control allowed in a screen's top line. */
@Composable
fun IconAction(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Rivals.colors.fg2,
) {
    Box(
        modifier = modifier
            .size(Space.touch)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription, tint = tint)
    }
}

/** A tinted chip in a player's colour, or a neutral one on `raised`. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Rivals.colors.fg2,
    background: Color = Rivals.colors.raised,
    leadingDot: Boolean = false,
) {
    Row(
        modifier = modifier
            .background(background, Shapes.chip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingDot) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Label(text, color = color)
    }
}

/** The live dot: the one place in the app, besides delete, that is red. */
@Composable
fun LiveChip(modifier: Modifier = Modifier) {
    Chip(
        text = "Live",
        modifier = modifier,
        color = Rivals.colors.live,
        background = Rivals.colors.liveTint,
        leadingDot = true,
    )
}

/** The round initial that stands in for a player's photo. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, background: Color = Rivals.colors.raised) {
    Box(
        modifier = modifier.size(40.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = Rivals.type.rowTitle.copy(fontFamily = Rivals.type.number.fontFamily),
            color = Rivals.colors.fg,
        )
    }
}

/**
 * A plain row: space and alignment separate it from the next one, never a line. [leading] is
 * usually an [Avatar] or a round icon; [trailing] a chevron, a score or a button.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Rivals.type.rowTitle, color = Rivals.colors.fg)
            if (subtitle != null) {
                Text(subtitle, style = Rivals.type.caption, color = Rivals.colors.fg3)
            }
        }
        trailing?.invoke(this)
    }
}

/** The round icon that leads a plain action row (Add a rival, Quick game). */
@Composable
fun RowIcon(iconRes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).background(Rivals.colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = Rivals.colors.fg)
    }
}

/** The chevron that says a row opens something. */
@Composable
fun RowChevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = Rivals.colors.fg3,
    )
}

/** A 16dp-radius `surface` panel. The only container in the app; there are no cards. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.background(Rivals.colors.surface, Shapes.panel).padding(Space.s16),
        verticalArrangement = Arrangement.spacedBy(Space.s8),
        content = content,
    )
}

/**
 * The written empty state every screen owes the reader: what isn't here, why, and the one thing
 * to do about it. Never a bare spinner, never a shrug.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s8),
    ) {
        Text(title, style = Rivals.type.title, color = Rivals.colors.fg, textAlign = TextAlign.Center)
        Text(
            body,
            style = Rivals.type.body,
            color = Rivals.colors.fg3,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.s8))
            action()
        }
    }
}

/** A loading state that says what it is waiting for. */
@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.s32),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, color = Rivals.colors.fg3)
    }
}

/** The whole screen, while it waits for its first snapshot. */
@Composable
fun FullScreenLoading(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Rivals.colors.base),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, color = Rivals.colors.fg3)
    }
}

/** Something went wrong, said in a sentence, with a way out. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        Text(
            message,
            style = Rivals.type.body,
            color = Rivals.colors.fg2,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) SecondaryButton("Try again", onRetry)
    }
}

/** A gap between sections, on the 4dp grid. */
@Composable
fun SectionGap(height: androidx.compose.ui.unit.Dp = Space.section) {
    Spacer(Modifier.height(height))
}

@Composable
fun HGap(width: androidx.compose.ui.unit.Dp = Space.s8) {
    Spacer(Modifier.width(width))
}
