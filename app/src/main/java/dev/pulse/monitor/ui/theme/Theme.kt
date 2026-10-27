package dev.pulse.monitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.pulse.monitor.R
import dev.pulse.monitor.data.settings.ThemeMode

/**
 * Pulse colour system. Each hardware category owns one accent so you can find things by
 * colour at a glance (always paired with a label or icon, never colour alone).
 */
@Immutable
data class PulseColors(
    val isDark: Boolean,
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val track: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val battery: Color,
    val cpu: Color,
    val ram: Color,
    val storage: Color,
    val network: Color,
    val networkAlt: Color,
    val heat: Color,
    val warning: Color,
)

val DarkPulseColors = PulseColors(
    isDark = true,
    background = Color(0xFF07090C),
    surface1 = Color(0xFF0D1116),
    surface2 = Color(0xFF131920),
    surface3 = Color(0xFF1A222B),
    outline = Color(0xFF242E39),
    track = Color(0xFF1C252E),
    textPrimary = Color(0xFFE9EEF3),
    textSecondary = Color(0xFF8C99A7),
    textTertiary = Color(0xFF5C6976),
    battery = Color(0xFF3DF5A0),
    cpu = Color(0xFFFF9F43),
    ram = Color(0xFFA78BFA),
    storage = Color(0xFF4D9BFF),
    network = Color(0xFF22D3EE),
    networkAlt = Color(0xFF7DF9FF),
    heat = Color(0xFFFF4D5E),
    warning = Color(0xFFFFC53D),
)

val LightPulseColors = PulseColors(
    isDark = false,
    background = Color(0xFFF2F4F7),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF8FAFC),
    surface3 = Color(0xFFEDF1F5),
    outline = Color(0xFFDCE2E9),
    track = Color(0xFFE3E8EE),
    textPrimary = Color(0xFF0D141B),
    textSecondary = Color(0xFF4F5D6B),
    textTertiary = Color(0xFF7D8A97),
    battery = Color(0xFF0E9F62),
    cpu = Color(0xFFDB6E00),
    ram = Color(0xFF7452DB),
    storage = Color(0xFF2667D9),
    network = Color(0xFF0A8EAD),
    networkAlt = Color(0xFF0B5F86),
    heat = Color(0xFFD9283C),
    warning = Color(0xFFB7791F),
)

/** Bold, wide display face (Saira Expanded) with tabular digits for numbers that change. */
val DisplayFamily = FontFamily(
    Font(R.font.saira_semiexpanded_medium, FontWeight.Medium),
    Font(R.font.saira_expanded_semibold, FontWeight.SemiBold),
    Font(R.font.saira_expanded_bold, FontWeight.Bold),
)

/** Clean label face (Inter). */
val LabelFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

@Immutable
data class PulseType(
    val hero: TextStyle,
    val bigNumber: TextStyle,
    val number: TextStyle,
    val smallNumber: TextStyle,
    val title: TextStyle,
    val sectionLabel: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
)

val DefaultPulseType = PulseType(
    hero = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 64.sp, fontFeatureSettings = TABULAR),
    bigNumber = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, fontFeatureSettings = TABULAR),
    number = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR),
    smallNumber = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR),
    title = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp),
    sectionLabel = TextStyle(fontFamily = LabelFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.6.sp),
    body = TextStyle(fontFamily = LabelFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR),
    bodyStrong = TextStyle(fontFamily = LabelFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR),
    caption = TextStyle(fontFamily = LabelFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR),
)

val LocalPulseColors = staticCompositionLocalOf { DarkPulseColors }
val LocalPulseType = staticCompositionLocalOf { DefaultPulseType }

object PulseTheme {
    val colors: PulseColors
        @Composable get() = LocalPulseColors.current
    val type: PulseType
        @Composable get() = LocalPulseType.current
}

/**
 * Colour for a temperature, shifting from the category colour through amber to red as
 * things heat up. `warnAtC` is where it becomes fully red.
 */
fun PulseColors.heatTint(base: Color, temperatureC: Float?, warnAtC: Float = 42f): Color {
    temperatureC ?: return base
    val start = warnAtC - 8f
    if (temperatureC <= start) return base
    val t = ((temperatureC - start) / 8f).coerceIn(0f, 1f)
    return if (t < 0.5f) lerp(base, warning, t * 2f) else lerp(warning, heat, (t - 0.5f) * 2f)
}

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun PulseTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkPulseColors else LightPulseColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.battery,
            onPrimary = Color(0xFF00210F),
            secondary = colors.network,
            tertiary = colors.ram,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface1,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface3,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface1,
            outline = colors.outline,
            error = colors.heat,
        )
    } else {
        lightColorScheme(
            primary = colors.battery,
            onPrimary = Color.White,
            secondary = colors.network,
            tertiary = colors.ram,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface1,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface3,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface1,
            outline = colors.outline,
            error = colors.heat,
        )
    }
    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = LabelFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = LabelFamily),
        bodySmall = base.bodySmall.copy(fontFamily = LabelFamily),
        labelLarge = base.labelLarge.copy(fontFamily = LabelFamily),
        labelMedium = base.labelMedium.copy(fontFamily = LabelFamily, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = LabelFamily),
        titleMedium = base.titleMedium.copy(fontFamily = DisplayFamily),
    )
    CompositionLocalProvider(
        LocalPulseColors provides colors,
        LocalPulseType provides DefaultPulseType,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
