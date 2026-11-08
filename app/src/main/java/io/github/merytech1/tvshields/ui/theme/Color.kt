package io.github.merytech1.tvshields.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class UiMode { DARK, LIGHT }

enum class Accent { PURPLE, EMERALD }

/** Color tokens from design.md §3, resolved for one mode and one accent. */
@Immutable
data class TvShieldsColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
)

private data class AccentTokens(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

private val purpleDark = AccentTokens(Color(0xFFC99BEB), Color(0xFF1E0B2B), Color(0xFF3A2150), Color(0xFFEBD3FA))
private val purpleLight = AccentTokens(Color(0xFF7E36AE), Color(0xFFFFFFFF), Color(0xFFF1E4FA), Color(0xFF4E1A72))
private val emeraldDark = AccentTokens(Color(0xFF3FCF94), Color(0xFF04170F), Color(0xFF143A2C), Color(0xFFA8F0CF))
private val emeraldLight = AccentTokens(Color(0xFF0A7A4F), Color(0xFFFFFFFF), Color(0xFFD3F2E3), Color(0xFF064D31))

fun tvShieldsColors(mode: UiMode, accent: Accent): TvShieldsColors {
    val a = when (accent) {
        Accent.PURPLE -> if (mode == UiMode.DARK) purpleDark else purpleLight
        Accent.EMERALD -> if (mode == UiMode.DARK) emeraldDark else emeraldLight
    }
    return when (mode) {
        UiMode.DARK -> TvShieldsColors(
            background = Color(0xFF0F1217),
            surface = Color(0xFF171B22),
            surfaceRaised = Color(0xFF20262F),
            border = Color(0xFF323A46),
            textPrimary = Color(0xFFE9EDF2),
            textSecondary = Color(0xFFA7B0BC),
            textTertiary = Color(0xFF7E8895),
            accent = a.accent,
            onAccent = a.onAccent,
            accentContainer = a.container,
            onAccentContainer = a.onContainer,
            warning = Color(0xFFE8B04A),
            onWarning = Color(0xFF1F1400),
            danger = Color(0xFFF2786D),
            onDanger = Color(0xFF240604),
        )
        UiMode.LIGHT -> TvShieldsColors(
            background = Color(0xFFF4F6F9),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFE9EDF2),
            border = Color(0xFFC9D0DA),
            textPrimary = Color(0xFF10151C),
            textSecondary = Color(0xFF46505D),
            textTertiary = Color(0xFF677180),
            accent = a.accent,
            onAccent = a.onAccent,
            accentContainer = a.container,
            onAccentContainer = a.onContainer,
            warning = Color(0xFF8A5A00),
            onWarning = Color(0xFFFFFFFF),
            danger = Color(0xFFB3261E),
            onDanger = Color(0xFFFFFFFF),
        )
    }
}
