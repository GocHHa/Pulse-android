package dev.pulse.monitor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pulse.monitor.R
import dev.pulse.monitor.ui.theme.PulseTheme

/** Scrolling screen body that respects the edge-to-edge insets coming from the Scaffold. */
@Composable
fun ScreenColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val dir = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(dir) + 16.dp,
            end = padding.calculateEndPadding(dir) + 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Mission-control style header: a small live "eyebrow" line above a wide display title,
 * with optional back and settings buttons.
 */
@Composable
fun ScreenHeader(
    title: String,
    eyebrow: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val colors = PulseTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            HeaderButton(R.drawable.ic_back, "Back") {
                haptics.tap()
                onBack()
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = eyebrow.uppercase(),
                    style = PulseTheme.type.sectionLabel,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = title,
                style = PulseTheme.type.title,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (onSettings != null) {
            HeaderButton(R.drawable.ic_settings, "Settings") {
                haptics.tap()
                onSettings()
            }
        }
    }
}

@Composable
private fun HeaderButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        PulseIcon(icon, contentDescription = description, tint = PulseTheme.colors.textPrimary, size = 22.dp)
    }
}
