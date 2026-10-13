package com.moneta.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale from ARCHITECTURE.md Section 3/9. Uses the system font family
 * (Roboto Flex) as a stand-in for Inter until a bundled font is added - the
 * SIZES and WEIGHTS below are what matter for consistency, more than the
 * specific family, and this keeps Phase 1 dependency-free.
 */
private val MonetaFontFamily = FontFamily.SansSerif

val MonetaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MonetaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp
    ),
    titleLarge = TextStyle(
        fontFamily = MonetaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = MonetaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MonetaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

/**
 * Feature flag for enabling tabular (monospaced-width) numerals for currency
 * figures (Section 3: "amounts align in columns"). Applied via
 * FontFeatureSettings where amounts are rendered - see MoneyText composable.
 */
const val TabularFiguresFontFeature = "tnum"
