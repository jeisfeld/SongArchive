package de.jeisfeld.songarchive.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
fun appTypography(): Typography {
    val isWide = LocalConfiguration.current.smallestScreenWidthDp > 450
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp > 600

    return Typography(
        bodyLarge = TextStyle(fontSize = if (isTablet) 20.sp else if (isWide) 18.sp else 16.sp),
        bodyMedium = TextStyle(fontSize = if (isTablet) 18.sp else if (isWide) 16.sp else 14.sp),
        bodySmall = TextStyle(fontSize = if (isTablet) 16.sp else if (isWide) 14.sp else 12.sp),
        titleLarge = TextStyle(fontSize = if (isTablet) 26.sp else if (isWide) 24.sp else 22.sp),
        titleMedium = TextStyle(fontSize = if (isTablet) 22.sp else if (isWide) 20.sp else 18.sp),
        titleSmall = TextStyle(fontSize = if (isTablet) 20.sp else if (isWide) 18.sp else 16.sp),
    )
}