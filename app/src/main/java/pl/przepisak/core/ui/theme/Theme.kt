package pl.przepisak.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import pl.przepisak.data.prefs.ThemeMode

// Paleta „kuchenna”: ciepła terakota jako kolor główny, zieleń zieleniny jako
// drugorzędny. Kontrasty trzymamy wysoko — z tej aplikacji korzysta się przy
// blacie, często z odległości i przy złym świetle.
private val Terracotta = Color(0xFFB4552F)
private val TerracottaLight = Color(0xFFFFDBCF)
private val TerracottaDark = Color(0xFF3D0F02)
private val TerracottaContainerDark = Color(0xFF8F3D1A)

private val Herb = Color(0xFF4F6642)
private val HerbLight = Color(0xFFD1EBBF)
private val HerbDark = Color(0xFF0F2005)
private val HerbContainerDark = Color(0xFF374E2B)

private val Honey = Color(0xFF745A2F)
private val HoneyLight = Color(0xFFFFDEAC)
private val HoneyDark = Color(0xFF281900)
private val HoneyContainerDark = Color(0xFF5A431A)

private val Cream = Color(0xFFFFF8F4)
private val CreamSurface = Color(0xFFFFF8F4)
private val CreamVariant = Color(0xFFF4DED4)
private val Charcoal = Color(0xFF1A120E)
private val CharcoalSurface = Color(0xFF201510)
private val CharcoalVariant = Color(0xFF52443C)

private val LightScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = TerracottaLight,
    onPrimaryContainer = TerracottaDark,
    secondary = Herb,
    onSecondary = Color.White,
    secondaryContainer = HerbLight,
    onSecondaryContainer = HerbDark,
    tertiary = Honey,
    onTertiary = Color.White,
    tertiaryContainer = HoneyLight,
    onTertiaryContainer = HoneyDark,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Cream,
    onBackground = Charcoal,
    surface = CreamSurface,
    onSurface = Charcoal,
    surfaceVariant = CreamVariant,
    onSurfaceVariant = Color(0xFF53433C),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD7C2B8),
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5C1A04),
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = TerracottaLight,
    secondary = Color(0xFFB5CFA4),
    onSecondary = Color(0xFF223516),
    secondaryContainer = HerbContainerDark,
    onSecondaryContainer = HerbLight,
    tertiary = Color(0xFFE4C28C),
    onTertiary = Color(0xFF412D04),
    tertiaryContainer = HoneyContainerDark,
    onTertiaryContainer = HoneyLight,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Charcoal,
    onBackground = Color(0xFFF1DFD8),
    surface = CharcoalSurface,
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = CharcoalVariant,
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFF9F8D84),
    outlineVariant = CharcoalVariant,
    scrim = Color.Black,
)

/**
 * Powiększenie czcionki obowiązujące w trybie gotowania (RF-11).
 * Poza tym trybem zostaje 1.0.
 */
val LocalKitchenFontScale = compositionLocalOf { 1f }

@Composable
fun PrzepisakTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        // Kolory z tapety systemowej istnieją dopiero od Androida 12.
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = PrzepisakTypography,
        content = content,
    )
}

/**
 * Powiększa typografię o podany współczynnik (RF-11).
 *
 * Skalujemy `MaterialTheme.typography`, a nie pojedyncze teksty — dzięki temu miejsce
 * wywołania nie musi znać wszystkich punktów, w których rysowany jest tekst.
 *
 * Owijamy nim wyłącznie treść przepisu (krok, lista składników), nie cały ekran.
 * Powiększony chrom — tytuł w pasku, etykiety przycisków — przy skali 2,0 rozsadza
 * układ, a z odległości ramienia czyta się treść, nie chrom.
 */
@Composable
fun KitchenTypography(scale: Float, content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val scaled = remember(base, scale) { base.scaledBy(scale) }
    CompositionLocalProvider(LocalKitchenFontScale provides scale) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = scaled,
            content = content,
        )
    }
}

private fun Typography.scaledBy(scale: Float): Typography = Typography(
    displayLarge = displayLarge.scaledBy(scale),
    displayMedium = displayMedium.scaledBy(scale),
    displaySmall = displaySmall.scaledBy(scale),
    headlineLarge = headlineLarge.scaledBy(scale),
    headlineMedium = headlineMedium.scaledBy(scale),
    headlineSmall = headlineSmall.scaledBy(scale),
    titleLarge = titleLarge.scaledBy(scale),
    titleMedium = titleMedium.scaledBy(scale),
    titleSmall = titleSmall.scaledBy(scale),
    bodyLarge = bodyLarge.scaledBy(scale),
    bodyMedium = bodyMedium.scaledBy(scale),
    bodySmall = bodySmall.scaledBy(scale),
    labelLarge = labelLarge.scaledBy(scale),
    labelMedium = labelMedium.scaledBy(scale),
    labelSmall = labelSmall.scaledBy(scale),
)

private fun TextStyle.scaledBy(scale: Float): TextStyle = copy(
    fontSize = fontSize * scale,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
)
