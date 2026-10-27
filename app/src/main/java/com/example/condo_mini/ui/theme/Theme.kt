package com.example.condo_mini.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Ivory,
    primaryContainer = InkSoft,
    onPrimaryContainer = Ivory,
    secondary = Slate,
    onSecondary = Paper,
    secondaryContainer = Linen,
    onSecondaryContainer = Ink,
    tertiary = Champagne,
    onTertiary = Paper,
    tertiaryContainer = ChampagneMist,
    onTertiaryContainer = Color(0xFF5C4524),
    background = Ivory,
    onBackground = Ink,
    surface = Ivory,
    onSurface = Ink,
    surfaceVariant = Linen,
    onSurfaceVariant = Slate,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Linen,
    surfaceContainerHigh = Sand,
    surfaceContainerHighest = Sand,
    outline = Mist,
    outlineVariant = Hairline,
    error = Burgundy,
    onError = Paper,
    errorContainer = BurgundyMist,
    onErrorContainer = Burgundy
)

private val DarkColorScheme = darkColorScheme(
    primary = Pearl,
    onPrimary = Night,
    primaryContainer = NightHigh,
    onPrimaryContainer = Pearl,
    secondary = PearlSoft,
    onSecondary = Night,
    secondaryContainer = NightHigh,
    onSecondaryContainer = Pearl,
    tertiary = ChampagneLight,
    onTertiary = Night,
    tertiaryContainer = Color(0xFF3A3122),
    onTertiaryContainer = ChampagneLight,
    background = Night,
    onBackground = Pearl,
    surface = Night,
    onSurface = Pearl,
    surfaceVariant = NightContainer,
    onSurfaceVariant = PearlSoft,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightContainer,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHigh,
    outline = Color(0xFF5A6170),
    outlineVariant = NightHairline,
    error = Color(0xFFE29A94),
    onError = Night,
    errorContainer = Color(0xFF3A1F20),
    onErrorContainer = Color(0xFFF1C4BF)
)

/** Cores fora do esquema Material: status e o gradiente "hero". */
@Immutable
data class CondoColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val accent: Color
)

private val LightCondoColors = CondoColors(
    success = Emerald,
    successContainer = EmeraldMist,
    warning = Amber,
    warningContainer = AmberMist,
    info = Sapphire,
    infoContainer = SapphireMist,
    heroStart = InkSoft,
    heroEnd = Ink,
    onHero = Ivory,
    onHeroMuted = Color(0xFFB7BCC6),
    accent = ChampagneLight
)

private val DarkCondoColors = CondoColors(
    success = Color(0xFF8CC7A8),
    successContainer = Color(0xFF1C3328),
    warning = Color(0xFFE0C077),
    warningContainer = Color(0xFF362D17),
    info = Color(0xFF9DB4DE),
    infoContainer = Color(0xFF1D2740),
    heroStart = Color(0xFF262D3C),
    heroEnd = Color(0xFF171B24),
    onHero = Pearl,
    onHeroMuted = PearlSoft,
    accent = ChampagneLight
)

private val LocalCondoColors = staticCompositionLocalOf { LightCondoColors }

val CondoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Acesso às cores estendidas: `CondoTheme.colors.success`. */
object CondoTheme {
    val colors: CondoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCondoColors.current
}

@Composable
fun CondominiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Sem cor dinâmica: a identidade da marca precisa ser igual em Android e iOS.
    CompositionLocalProvider(
        LocalCondoColors provides if (darkTheme) DarkCondoColors else LightCondoColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = CondoShapes,
            content = content
        )
    }
}
