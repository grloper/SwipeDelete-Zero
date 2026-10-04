package com.swipedelete.zero.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.swipedelete.zero.R

/** Bundled Inter throughout, including Material controls. */
val BodyFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val DisplayFamily = BodyFamily
object SdzType {
    val HeroNumber = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, lineHeight = 68.sp, fontFeatureSettings = "tnum")
    val StatNumber = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum")
    val Title = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp)
    val Subtitle = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp)
    val Row = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val BodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val Label = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)
    val Numeric = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
    val LabelSmall = Numeric
    val Overline = Numeric
}
val SdzTypography = Typography(
    displayLarge = SdzType.HeroNumber, displayMedium = SdzType.HeroNumber, displaySmall = SdzType.StatNumber,
    headlineLarge = SdzType.Title, headlineMedium = SdzType.Title, headlineSmall = SdzType.Subtitle,
    titleLarge = SdzType.Title, titleMedium = SdzType.Subtitle, titleSmall = SdzType.Row,
    bodyLarge = SdzType.Body, bodyMedium = SdzType.BodySmall, bodySmall = SdzType.Numeric,
    labelLarge = SdzType.Label, labelMedium = SdzType.Numeric, labelSmall = SdzType.Numeric,
)
