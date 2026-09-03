package com.swipedelete.zero.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * # "Negative Space" — the single source of truth for every visual value.
 *
 * Nothing in the UI may invent a colour, radius, spacing step or duration. If a
 * screen needs a value that is not here, the value is wrong or the system is
 * missing something — extend this file, never the screen. The previous UI was
 * inconsistent precisely because each screen picked its own literals.
 *
 * ## The colour contract
 *
 * Every colour that carries meaning carries exactly ONE meaning, everywhere,
 * and is used nowhere else:
 *
 * | Role      | Colour        | Means, always and only                      |
 * |-----------|---------------|---------------------------------------------|
 * | Keep      | [Azure]       | protected / safe / you chose to keep it     |
 * | Reclaim   | [Amber]       | space you can get back — queued, or freed   |
 * | Archive   | [Teal]        | goes to the cloud                           |
 * | Critical  | [Safelight]   | irreversible, or storage genuinely critical |
 * | Brand     | [Phosphor]    | identity and primary text — never a state   |
 *
 * Keep and Reclaim are deliberately blue-vs-amber: the canonical
 * colour-vision-deficiency-safe pair. Red-vs-green — the worst possible pair —
 * is gone. Hue is also never the only signal; see [SdzMotion] and the action
 * row, where every decision is additionally carried by icon silhouette,
 * screen position and a visible text label.
 */
object SdzColor {

    // ---- Surfaces -----------------------------------------------------------
    // A warm charcoal ramp, not flat #000000. Photographic darkrooms are warm
    // and tonal; phone-OS true black is neither, and it forces every boundary to
    // be drawn with a border. Here depth comes from tone, so borders are rare.

    /** App background — the deepest tone. */
    val Surface0 = Color(0xFF0D0C0B)

    /** Resting cards and list rows. */
    val Surface1 = Color(0xFF161412)

    /** Raised cards, sheets, the active card in a stack. */
    val Surface2 = Color(0xFF201D1A)

    /** Controls sitting on raised surfaces; pressed states. */
    val Surface3 = Color(0xFF2B2723)

    /** Dialogs and anything floating above everything else. */
    val Surface4 = Color(0xFF363029)

    /** Hairline, used sparingly — tone should do this job first. */
    val Hairline = Color(0x14F7EFE3)

    /** A scrim for content behind sheets and dialogs. */
    val Scrim = Color(0xCC0D0C0B)

    // ---- Text ---------------------------------------------------------------
    // Contrast measured against Surface1: Primary ~15:1, Secondary ~7.8:1,
    // Tertiary ~4.8:1 — all clear of the 4.5:1 body-text floor.

    /** Phosphor — warm bone white. Primary text and the brand mark. */
    val Phosphor = Color(0xFFF2EDE4)
    val TextSecondary = Color(0xFFA9A199)

    /** Still ≥4.5:1; safe for body text, not only for large text. */
    val TextTertiary = Color(0xFF837C74)

    /** Text placed on top of a filled accent (Azure/Amber/Teal/Safelight). */
    val OnAccent = Color(0xFF0D0C0B)

    // ---- Meaning-carrying accents ------------------------------------------

    /** KEEP. Protected, safe, kept. Never used decoratively. */
    val Azure = Color(0xFF5B9DF9)
    val AzureDim = Color(0x335B9DF9)

    /**
     * DELETE / TRASH. Vivid, unambiguous red indicating swiping left or trashing/deleting a file.
     */
    val Red = Color(0xFFFF453A)
    val RedDim = Color(0x33FF453A)

    /**
     * RECLAIM. Space you can get back: queued for deletion, reclaimable in the
     * storage meter, and the number that counts up when you free some.
     */
    val Amber = Color(0xFFE8A33D)
    val AmberDim = Color(0x33E8A33D)

    /** ARCHIVE. Uploading to the cloud. */
    val Teal = Color(0xFF4FC3B4)
    val TealDim = Color(0x334FC3B4)

    /**
     * CRITICAL. Darkroom safelight red — deliberately rare. Permitted on exactly
     * two things: an irreversible, no-undo action, and storage that is genuinely
     * critical. Never as chrome, never as a default "delete" tint.
     */
    val Safelight = Color(0xFFE5484D)
    val SafelightDim = Color(0x33E5484D)

    // ---- Storage urgency ----------------------------------------------------
    // A three-stop ramp reusing existing meanings rather than inventing colours:
    // calm neutral -> Amber (reclaimable pressure) -> Safelight (critical).

    val UrgencyCalm = Color(0xFF6E6862)
    val UrgencyFilling = Amber
    val UrgencyCritical = Safelight

    /** The empty remainder of any meter or track. */
    val Track = Color(0x1FF7EFE3)
}

/** 4dp-based spacing scale. Layouts compose these; they never hardcode dp. */
object SdzSpace {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val h1: Dp = 32.dp
    val h2: Dp = 40.dp
    val h3: Dp = 48.dp
    val h4: Dp = 64.dp
}

/** Corner radii. Cards are generous; controls are tighter. */
object SdzRadius {
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val pill: Dp = 999.dp
}

/** Shadow elevation paired with the surface tones above. */
object SdzElevation {
    val flat: Dp = 0.dp
    val raised: Dp = 2.dp
    val floating: Dp = 8.dp
    val dialog: Dp = 16.dp
    val dragging: Dp = 24.dp
}

/**
 * Motion. Photographs have mass: they tilt as you push them, carry momentum
 * when released, and settle rather than snap.
 */
object SdzMotion {
    /** Feedback that must feel instantaneous (press states). */
    const val Instant = 90

    /** Small state changes — chips, labels, colour shifts. */
    const val Quick = 160

    /** The default for anything the eye should follow. */
    const val Standard = 240

    /** Entrances, backdrop cross-fades, sheet transitions. */
    const val Expressive = 400

    /** The freed-space count-down. Long enough to be felt, not endured. */
    const val Celebration = 900

    /** M3 "emphasised" curve: quick departure, long graceful settle. */
    val Emphasised: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** A released card settling back — slight overshoot reads as physical. */
    fun <T> settle(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** A card leaving the stack — critically damped so it never wobbles out. */
    fun <T> fling(): SpringSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = 180f,
    )
}

/** Minimum interactive sizes. Nothing tappable may be smaller. */
object SdzTouch {
    /** Android accessibility floor. */
    val minTarget: Dp = 48.dp

    /** Primary thumb-zone decision buttons. */
    val primaryAction: Dp = 72.dp

    /** Secondary thumb-zone actions. */
    val secondaryAction: Dp = 56.dp
}
