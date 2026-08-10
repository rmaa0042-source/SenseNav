package com.flip6.sensenav.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Kept in step with the palette the screens use, so Material components
// (buttons, text fields, progress indicators) match the hand-styled surfaces.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2F5FBD),
    secondary = Color(0xFF5A6B8C),
    tertiary = Color(0xFFFF4F86),
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF1F2633),
    onSurface = Color(0xFF1F2633)
)

/**
 * SenseNav renders on hardcoded white surfaces with dark ink text, so the colour
 * scheme is pinned to light.
 *
 * Following the system dark theme (or Material You dynamic colour) flips
 * `onSurface` to near-white while those surfaces stay white, which made text
 * fields render white-on-white. Restoring dark mode means giving the screens
 * theme-driven backgrounds first - `MaterialTheme.colorScheme.surface` instead of
 * `Color.White` - rather than re-enabling these flags.
 */
@Composable
fun SenseNavTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}