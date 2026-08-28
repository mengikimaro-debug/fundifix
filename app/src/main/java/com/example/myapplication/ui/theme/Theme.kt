package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    secondary = CyanDeep,
    tertiary = AmberStar,
    background = BgColor,
    surface = BgCard,
    surfaceVariant = BgElevated,
    onBackground = TextHi,
    onSurface = TextHi,
    onSurfaceVariant = TextLo,
    outline = LineColor
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF241507),
    secondary = CyanDeep,
    onSecondary = Color.White,
    tertiary = AmberStar,
    onTertiary = Color(0xFF1A1200),
    background = BgColor,
    surface = BgCard,
    surfaceVariant = BgElevated,
    onBackground = TextHi,
    onSurface = TextHi,
    onSurfaceVariant = TextLo,
    outline = LineColor
)

private val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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
        shapes = OneUiShapes,
        content = content
    )
}