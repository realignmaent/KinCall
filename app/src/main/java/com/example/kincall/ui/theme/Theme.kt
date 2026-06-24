package com.example.kincall.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * KinCall 浅色主题
 *
 * 设计原则：
 * - 老人白天使用手机，不需要深色主题
 * - 高对比度，易于辨识
 * - 温暖色调，亲切友好
 */
private val KinCallLightColorScheme = lightColorScheme(
    primary = KinCallPrimary,
    onPrimary = Color.White,
    primaryContainer = KinCallPrimaryLight,
    onPrimaryContainer = KinCallPrimaryDark,
    secondary = KinCallSecondary,
    onSecondary = Color.White,
    secondaryContainer = KinCallSecondaryLight,
    onSecondaryContainer = KinCallSecondaryDark,
    background = KinCallBackground,
    onBackground = KinCallOnSurface,
    surface = KinCallSurface,
    onSurface = KinCallOnSurface,
    surfaceVariant = Color(0xFFF0EDE8),
    onSurfaceVariant = KinCallOnSurfaceVariant,
    error = KinCallError,
    onError = Color.White,
    errorContainer = KinCallErrorLight,
    onErrorContainer = Color.White,
)

/**
 * KinCall 主题入口
 * 整个 App 使用此主题，不支持深色模式
 */
@Composable
fun KinCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KinCallLightColorScheme,
        content = content
    )
}
