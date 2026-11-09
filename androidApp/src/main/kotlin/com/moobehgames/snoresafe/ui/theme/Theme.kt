package com.moobehgames.snoresafe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.moobehgames.snoresafe.core.analysis.SnoreBand

private val Night = darkColorScheme(
    primary = Color(0xFF5EC2B7),
    onPrimary = Color(0xFF00201D),
    secondary = Color(0xFF9DB4E0),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFDCE3F0),
    surface = Color(0xFF121A2E),
    onSurface = Color(0xFFDCE3F0),
    surfaceVariant = Color(0xFF1B2540),
    onSurfaceVariant = Color(0xFFAEB9CF),
    surfaceContainerLowest = Color(0xFF080C18),
    surfaceContainerLow = Color(0xFF0F1628),
    surfaceContainer = Color(0xFF131C31),
    surfaceContainerHigh = Color(0xFF172138),
    surfaceContainerHighest = Color(0xFF1B2640),
    secondaryContainer = Color(0xFF1F3B44),
    onSecondaryContainer = Color(0xFFCDEBE6),
    primaryContainer = Color(0xFF16413C),
    onPrimaryContainer = Color(0xFFBDEFE7),
    outline = Color(0xFF55617A),
    outlineVariant = Color(0xFF2A3550),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Day = lightColorScheme(
    primary = Color(0xFF006A62),
    onPrimary = Color.White,
    secondary = Color(0xFF4A5F8C),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF151B2C),
    surface = Color.White,
    onSurface = Color(0xFF151B2C),
    surfaceVariant = Color(0xFFE3E8F2),
    onSurfaceVariant = Color(0xFF444B5E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5FA),
    surfaceContainer = Color(0xFFECF0F7),
    surfaceContainerHigh = Color(0xFFE6EBF3),
    surfaceContainerHighest = Color(0xFFE0E6EF),
    secondaryContainer = Color(0xFFD3ECE8),
    onSecondaryContainer = Color(0xFF0F2E2A),
    primaryContainer = Color(0xFFBDEFE7),
    onPrimaryContainer = Color(0xFF00201D),
    outline = Color(0xFF7A8396),
    outlineVariant = Color(0xFFC8CFDC),
)

/** Day screens follow the system; [forceNight] keeps recording screens dark and low-glare. */
@Composable
fun SnoreSafeTheme(forceNight: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (forceNight || isSystemInDarkTheme()) Night else Day, content = content)
}

object BandColors {
    val quiet = Color(0xFF3A4A6B)
    val light = Color(0xFF7FC8A9)
    val loud = Color(0xFFF2B65A)
    val epic = Color(0xFFE5675C)

    fun of(band: SnoreBand): Color = when (band) {
        SnoreBand.QUIET -> quiet
        SnoreBand.LIGHT -> light
        SnoreBand.LOUD -> loud
        SnoreBand.EPIC -> epic
    }
}
