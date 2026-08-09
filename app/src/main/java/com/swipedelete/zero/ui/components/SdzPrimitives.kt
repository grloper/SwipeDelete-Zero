package com.swipedelete.zero.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzElevation
import com.swipedelete.zero.ui.theme.SdzMotion
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzTouch
import com.swipedelete.zero.ui.theme.SdzType

/**
 * The shared building blocks. Every screen composes these; no screen restyles
 * one locally. If something needs a variant, the variant is added here so it
 * exists once and stays consistent everywhere it appears.
 */

/** Which tonal step a surface sits on. Depth is tone first, shadow second. */
enum class SdzLevel(val color: Color, val shadow: Dp) {
    Base(SdzColor.Surface0, SdzElevation.flat),
    Card(SdzColor.Surface1, SdzElevation.flat),
    Raised(SdzColor.Surface2, SdzElevation.raised),
    Floating(SdzColor.Surface3, SdzElevation.floating),
    Dialog(SdzColor.Surface4, SdzElevation.dialog),
}

/**
 * The one card in the app. Borders are opt-in and rare — tone separates
 * surfaces, which is what stops the UI reading as a grid of outlined boxes.
 */
@Composable
fun SdzSurface(
    modifier: Modifier = Modifier,
    level: SdzLevel = SdzLevel.Card,
    radius: Dp = SdzRadius.lg,
    outlined: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = SdzSpace.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var base = modifier
    if (level.shadow > 0.dp) base = base.shadow(level.shadow, shape, clip = false)
    base = base.clip(shape).background(level.color)
    if (accent != null) {
        base = base.border(1.dp, accent.copy(alpha = 0.45f), shape)
    } else if (outlined) {
        base = base.border(1.dp, SdzColor.Hairline, shape)
    }
    if (onClick != null) base = base.clickable(onClick = onClick)

    Column(
        modifier = base.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(SdzSpace.sm),
        content = content,
    )
}

/** Button emphasis. Exactly one Primary should be visible per screen. */
enum class SdzButtonStyle { Primary, Secondary, Tertiary, Destructive }

/**
 * The one button. [SdzButtonStyle.Destructive] is the only place the safelight
 * red is permitted, and it is reserved for actions that cannot be undone.
 */
@Composable
fun SdzButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SdzButtonStyle = SdzButtonStyle.Primary,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 900f),
        label = "button-press",
    )
    val shape = RoundedCornerShape(SdzRadius.md)

    val container: Color
    val content: Color
    val borderColor: Color?
    when (style) {
        SdzButtonStyle.Primary -> {
            container = SdzColor.Azure; content = SdzColor.OnAccent; borderColor = null
        }
        SdzButtonStyle.Secondary -> {
            container = SdzColor.Surface3; content = SdzColor.Phosphor; borderColor = null
        }
        SdzButtonStyle.Tertiary -> {
            container = Color.Transparent; content = SdzColor.TextSecondary
            borderColor = SdzColor.Hairline
        }
        SdzButtonStyle.Destructive -> {
            container = Color.Transparent; content = SdzColor.Safelight
            borderColor = SdzColor.Safelight
        }
    }
    val alpha = if (enabled) 1f else 0.4f

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = SdzTouch.minTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(container.copy(alpha = container.alpha * alpha))
            .then(
                if (borderColor != null) Modifier.border(1.5.dp, borderColor.copy(alpha = alpha), shape)
                else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = SdzSpace.xl, vertical = SdzSpace.md),
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        }
        Text(label, style = SdzType.Label, color = content.copy(alpha = alpha))
    }
}

/**
 * A labelled circular action. The label is *visible*, not just an accessible
 * name — the previous action row was four unlabelled circles whose cloud icon
 * nobody could reliably interpret.
 */
@Composable
fun SdzCircleAction(
    icon: Painter,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = SdzTouch.primaryAction,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 800f),
        label = "action-press",
    )
    val alpha = if (enabled) 1f else 0.35f

    Column(
        modifier = modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SdzSpace.xs),
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(SdzRadius.pill))
                .background(if (filled) accent.copy(alpha = alpha) else SdzColor.Surface2)
                .border(
                    width = if (filled) 0.dp else 1.5.dp,
                    color = if (filled) Color.Transparent else accent.copy(alpha = alpha),
                    shape = RoundedCornerShape(SdzRadius.pill),
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (filled) SdzColor.OnAccent.copy(alpha = alpha) else accent.copy(alpha = alpha),
                modifier = Modifier.size(diameter * 0.38f),
            )
        }
        Text(
            label,
            style = SdzType.LabelSmall,
            color = if (enabled) SdzColor.TextSecondary else SdzColor.TextTertiary,
        )
    }
}

/**
 * An icon-only control that is nonetheless always labelled and always big
 * enough. Bare `Icon(...).clickable` produced 22-26dp targets in several
 * places, well under the 48dp accessibility floor; this keeps the glyph small
 * while the *target* stays compliant.
 */
@Composable
fun SdzIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = SdzColor.Phosphor,
    glyphSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(SdzTouch.minTarget)
            .clip(RoundedCornerShape(SdzRadius.pill))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = icon, contentDescription = null, tint = tint, modifier = Modifier.size(glyphSize))
    }
}

/** Small selectable pill — used for sorts, filters and the bucket/sprint lens toggle. */
@Composable
fun SdzChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = SdzColor.Phosphor,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SdzRadius.pill)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = SdzTouch.minTarget)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else SdzColor.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = SdzSpace.lg, vertical = SdzSpace.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = SdzType.Label,
            color = if (selected) accent else SdzColor.TextSecondary,
        )
    }
}

/** Section heading with an optional count. Used identically on every screen. */
@Composable
fun SdzSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = SdzType.Subtitle, color = SdzColor.Phosphor)
        trailing?.invoke()
    }
}

/** Screen top bar. One implementation, so back affordances never drift. */
@Composable
fun SdzTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SdzSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(SdzTouch.minTarget)
                    .clip(RoundedCornerShape(SdzRadius.pill))
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = SdzIcons.Back,
                    contentDescription = null,
                    tint = SdzColor.Phosphor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = SdzType.Title, color = SdzColor.Phosphor)
            if (subtitle != null) {
                Text(subtitle, style = SdzType.Numeric, color = SdzColor.TextSecondary)
            }
        }
        trailing?.invoke()
    }
}
