package io.github.merytech1.tvshields.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

val LocalTvShieldsColors = staticCompositionLocalOf { tvShieldsColors(UiMode.DARK, Accent.PURPLE) }

/** Mode and accent are user settings (design.md §2); dark + purple is the default. */
@Composable
fun TvShieldsTheme(
    mode: UiMode = UiMode.DARK,
    accent: Accent = Accent.PURPLE,
    content: @Composable () -> Unit,
) {
    val c = tvShieldsColors(mode, accent)
    val scheme = if (mode == UiMode.DARK) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentContainer,
            onPrimaryContainer = c.onAccentContainer,
            background = c.background,
            onBackground = c.textPrimary,
            surface = c.surface,
            onSurface = c.textPrimary,
            surfaceVariant = c.surfaceRaised,
            onSurfaceVariant = c.textSecondary,
            border = c.border,
            error = c.danger,
            onError = c.onDanger,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentContainer,
            onPrimaryContainer = c.onAccentContainer,
            background = c.background,
            onBackground = c.textPrimary,
            surface = c.surface,
            onSurface = c.textPrimary,
            surfaceVariant = c.surfaceRaised,
            onSurfaceVariant = c.textSecondary,
            border = c.border,
            error = c.danger,
            onError = c.onDanger,
        )
    }
    CompositionLocalProvider(LocalTvShieldsColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = TvShieldsTypography, content = content)
    }
}
