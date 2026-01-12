package com.example.casheye.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun CasheyeTheme(
    // 常にダークモードを優先したい場合は darkTheme を true に固定するか、
    // システム設定に従いつつ dynamicColor をオフにします
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // ★ ここを false に変更 [cite: 2026-01-05]
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // dynamicColor が false なら、ここはスキップされます
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 背景色を強制的に固定したい場合は、Surface でラップするのが最も確実です [cite: 2026-01-05]
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background // ここでテーマの背景色（Darkなら黒系）を適用 [cite: 2026-01-05]
        ) {
            content()
        }
    }
}