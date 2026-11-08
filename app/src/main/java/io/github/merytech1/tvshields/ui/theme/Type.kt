package io.github.merytech1.tvshields.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// design.md §4. FontFamily.Default is Roboto on Android TV.
private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

val TvShieldsTypography = Typography(
    displayLarge = style(36, 44),
    headlineMedium = style(28, 36),
    titleMedium = style(22, 28, FontWeight.Medium),
    bodyLarge = style(18, 26),
    labelLarge = style(18, 24, FontWeight.Medium),
)
