package com.swipedelete.zero.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.swipedelete.zero.R

/**
 * Two families, one rule.
 *
 * **Display — Space Grotesk.** Used for exactly three things: numeric values the
 * user is meant to feel, screen titles, and the wordmark. It descends from Space
 * Mono, so its figures have the squared, instrument-panel character that suits a
 * light meter or a film canister, and it holds up at 56sp where a neutral UI
 * face goes bland.
 *
 * **Body — Inter.** Everything else. It was drawn for screen UI at small sizes
 * and stays legible at 11sp, which Space Grotesk does not.
 *
 * Both are bundled rather than fetched, because downloadable fonts would route
 * through Play Services and break the fdroid build's air-gap guarantee.
 *
 * Every numeric style sets `tnum` (tabular figures) so counters do not jitter
 * horizontally while they animate — a count-down that reflows is unreadable.
 */

val DisplayFamily = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val BodyFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

/**
 * Named styles the app actually uses. Screens reference these, not raw sp
 * values, so the scale stays a scale.
 */
object SdzType {

    /** The emotional centrepiece: freed space, storage totals. Never for prose. */
    val HeroNumber = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.6).sp,
        fontFeatureSettings = TABULAR,
    )

    /** A secondary large number — bucket totals, sprint headline figures. */
    val StatNumber = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = TABULAR,
    )

    /** Screen titles. */
    val Title = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    )

    /** Section headings and card titles. */
    val Subtitle = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )

    val Body = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    val BodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** Button and action labels. */
    val Label = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    )

    /** The visible caption under an action button. */
    val LabelSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    )

    /** Inline data readouts: sizes, counts, durations. Tabular so lists align. */
    val Numeric = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontFeatureSettings = TABULAR,
    )

    /** Small all-caps eyebrow above a heading. */
    val Overline = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    )
}

/** Material 3 components map onto the same scale so nothing falls back to Roboto. */
val SdzTypography = Typography(
    displayLarge = SdzType.HeroNumber,
    displayMedium = SdzType.StatNumber,
    headlineMedium = SdzType.Title,
    titleLarge = SdzType.Title,
    titleMedium = SdzType.Subtitle,
    bodyLarge = SdzType.Body,
    bodyMedium = SdzType.BodySmall,
    labelLarge = SdzType.Label,
    labelMedium = SdzType.Numeric,
    labelSmall = SdzType.LabelSmall,
)
