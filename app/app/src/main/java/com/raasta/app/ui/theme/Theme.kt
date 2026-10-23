package com.raasta.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RaastaDarkColorScheme = darkColorScheme(
    primary = ElectricLime,
    onPrimary = Obsidian,
    primaryContainer = ElectricLime,
    onPrimaryContainer = Obsidian,
    secondary = ElectricLime,
    onSecondary = Obsidian,
    secondaryContainer = ElectricLime,
    onSecondaryContainer = Obsidian,
    tertiary = AttentionAmber,
    onTertiary = Obsidian,
    background = Obsidian,
    onBackground = Eggshell,
    surface = Obsidian,
    onSurface = Eggshell,
    surfaceVariant = ObsidianRaised,
    onSurfaceVariant = MutedOnDark,
    surfaceContainerLowest = Obsidian,
    surfaceContainerLow = ObsidianRaised,
    surfaceContainer = ObsidianRaised,
    surfaceContainerHigh = ObsidianHigh,
    surfaceContainerHighest = ObsidianHigh,
    outline = OutlineOnDark,
    outlineVariant = OutlineOnDark,
    error = ErrorOnDark,
    onError = Obsidian,
)

// Light scheme for daytime use.
private val RaastaLightColorScheme = lightColorScheme(
    primary = Obsidian,
    onPrimary = CardWhite,
    primaryContainer = ElectricLime,
    onPrimaryContainer = Obsidian,
    secondary = ElectricLime,
    onSecondary = Obsidian,
    secondaryContainer = ElectricLime,
    onSecondaryContainer = Obsidian,
    tertiary = AttentionAmber,
    onTertiary = Obsidian,
    background = Eggshell,
    onBackground = Obsidian,
    surface = CardWhite,
    onSurface = Obsidian,
    onSurfaceVariant = MutedSlate,
    outline = OutlineOnLight,
    outlineVariant = OutlineOnLight,
    error = DangerRed,
    onError = CardWhite,
)

/** Raasta is dark-first: it is mostly used outdoors at night, so dark is the default. */
@Composable
fun RaastaTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) RaastaDarkColorScheme else RaastaLightColorScheme,
        shapes = RaastaShapes,
        typography = RaastaTypography,
        content = content,
    )
}
