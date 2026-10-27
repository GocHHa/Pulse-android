package dev.pulse.monitor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pulse.monitor.R
import dev.pulse.monitor.ui.theme.PulseTheme

const val NOT_AVAILABLE = "Not available on this device"

/**
 * The basic building block: a layered "instrument panel" with a soft gradient, a hairline
 * border and a glowing accent edge in the category colour. Presses squish slightly with a
 * spring, like a physical button.
 */
@Composable
fun InstrumentCard(
    modifier: Modifier = Modifier,
    accent: Color = PulseTheme.colors.textTertiary,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PulseTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "cardPress",
    )
    val shape = RoundedCornerShape(24.dp)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            role = Role.Button,
        ) {
            haptics.tap()
            onClick()
        }
    } else Modifier

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.surface2, colors.surface1)))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(colors.outline, colors.outline.copy(alpha = 0.35f))),
                shape = shape,
            )
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, accent, accent.copy(alpha = 0f)),
                        startX = 0f,
                        endX = size.width * 0.7f,
                    ),
                    start = Offset(0f, stroke / 2),
                    end = Offset(size.width * 0.7f, stroke / 2),
                    strokeWidth = stroke,
                )
            }
            .then(clickModifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Row(modifier = modifier.semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        if (accent != null) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = PulseTheme.type.sectionLabel,
            color = PulseTheme.colors.textSecondary,
        )
    }
}

@Composable
fun PulseIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Icon(
        painter = painterResource(res),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** Clean placeholder for readings the device doesn't expose. */
@Composable
fun NotAvailable(modifier: Modifier = Modifier, reason: String? = null, compact: Boolean = false) {
    val colors = PulseTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PulseIcon(R.drawable.ic_info, contentDescription = null, tint = colors.textTertiary, size = if (compact) 14.dp else 16.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = NOT_AVAILABLE,
                style = if (compact) PulseTheme.type.caption else PulseTheme.type.body,
                color = colors.textTertiary,
            )
            if (reason != null) {
                Text(text = reason, style = PulseTheme.type.caption, color = colors.textTertiary.copy(alpha = 0.8f))
            }
        }
    }
}

/** A label/value line in a details card. A null value shows the "not available" state. */
@Composable
fun StatRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = PulseTheme.colors.textPrimary,
    @DrawableRes icon: Int? = null,
    iconTint: Color = PulseTheme.colors.textSecondary,
    supporting: String? = null,
) {
    val colors = PulseTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PulseIcon(icon, contentDescription = null, tint = iconTint, size = 18.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text = label, style = PulseTheme.type.body, color = colors.textSecondary)
            if (supporting != null) {
                Text(text = supporting, style = PulseTheme.type.caption, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (value != null) {
            Text(
                text = value,
                style = PulseTheme.type.bodyStrong,
                color = valueColor,
                textAlign = TextAlign.End,
            )
        } else {
            Text(
                text = NOT_AVAILABLE,
                style = PulseTheme.type.caption,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PulseTheme.colors.outline.copy(alpha = 0.6f)),
    )
}

/** Blinking "live" indicator, like a recording light. */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveAlpha",
    )
    Canvas(modifier.size(size)) {
        drawCircle(color.copy(alpha = alpha * 0.35f), radius = this.size.minDimension)
        drawCircle(color.copy(alpha = alpha), radius = this.size.minDimension / 2)
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PulseIcon(icon, contentDescription = null, tint = color, size = 14.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text.uppercase(),
            style = PulseTheme.type.sectionLabel,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ascending signal bars; the level is also announced as text for accessibility. */
@Composable
fun SignalBars(level: Int?, color: Color, modifier: Modifier = Modifier, max: Int = 4) {
    val track = PulseTheme.colors.track
    val description = if (level == null) "Signal strength unknown" else "Signal $level of $max bars"
    Canvas(
        modifier
            .size(width = 26.dp, height = 18.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (max - 1)) / max
        for (i in 0 until max) {
            val h = size.height * (i + 1) / max
            drawRoundRect(
                color = if (level != null && i < level) color else track,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 3, barW / 3),
            )
        }
    }
}

/** Animated horizontal meter (temperature bar, storage, etc.). */
@Composable
fun LinearMeter(
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val track = PulseTheme.colors.track
    val animated by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "meter",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = r)
        if (fraction != null && animated > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)),
                size = Size(size.width * animated, size.height),
                cornerRadius = r,
            )
        }
    }
}

/**
 * Pill-shaped segmented selector with a sliding highlight that moves on a spring.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = PulseTheme.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surface3)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Tab) {
                            if (!selected) {
                                haptics.tick()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = PulseTheme.type.bodyStrong,
                        color = if (selected) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Big value + unit + caption used inside dashboard cards. */
@Composable
fun MetricBlock(
    value: String,
    unit: String?,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = PulseTheme.type.number, color = color)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = PulseTheme.type.caption,
                    color = PulseTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Text(text = caption, style = PulseTheme.type.caption, color = PulseTheme.colors.textSecondary)
    }
}

@Composable
fun RowScope.Gap(width: Dp = 12.dp) = Spacer(Modifier.width(width))

@Composable
fun ColumnScope.VGap(height: Dp = 12.dp) = Spacer(Modifier.height(height))

/** Spacing between screen sections. */
val ScreenPadding = PaddingValues(horizontal = 16.dp)

val SectionSpacing = Arrangement.spacedBy(12.dp)
