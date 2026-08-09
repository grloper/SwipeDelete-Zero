package com.swipedelete.zero.ui.components

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.decode.VideoFrameDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.ui.theme.SdzColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Two-stop ambience derived from the active card's dominant color. */
data class CardPalette(val top: Color, val mid: Color)

/** Neutral obsidian ambience shown before extraction lands / for null items. */
val NeutralCardPalette = CardPalette(
    top = SdzColor.Surface1,
    mid = SdzColor.Surface0,
)

// Session-wide memo so revisiting a card (or Undo) never re-extracts.
private val paletteCache = LruCache<Long, CardPalette>(128)

/**
 * Extracts the dominant palette of [item] and blends it toward pitch black so
 * the OLED base always survives.
 *
 * Performance guardrails: a dedicated 64px software-bitmap Coil request (the
 * full-size card image stays on the fast hardware path — Palette cannot read
 * hardware bitmaps), a 120 ms debounce so rapid flings skip extraction
 * entirely, and an LRU memo keyed by media id.
 */
@Composable
fun rememberDominantColors(item: MediaItem?): State<CardPalette> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(paletteFor(item) ?: NeutralCardPalette) }

    LaunchedEffect(item?.id) {
        val target = item ?: run {
            state.value = NeutralCardPalette
            return@LaunchedEffect
        }
        paletteCache.get(target.id)?.let {
            state.value = it
            return@LaunchedEffect
        }
        delay(120)

        val request = ImageRequest.Builder(context)
            .data(target.contentUri)
            .size(64)
            .allowHardware(false)
            .apply { if (target.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@LaunchedEffect
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect

        val palette = withContext(Dispatchers.Default) {
            Palette.from(bitmap).maximumColorCount(12).generate()
        }
        val swatch = palette.darkVibrantSwatch
            ?: palette.vibrantSwatch
            ?: palette.darkMutedSwatch
            ?: palette.mutedSwatch
            ?: return@LaunchedEffect
        val base = Color(swatch.rgb)
        val cardPalette = CardPalette(
            top = lerp(base, SdzColor.Surface0, 0.55f),
            mid = lerp(base, SdzColor.Surface0, 0.82f),
        )
        paletteCache.put(target.id, cardPalette)
        state.value = cardPalette
    }
    return state
}

private fun paletteFor(item: MediaItem?): CardPalette? = item?.let { paletteCache.get(it.id) }

/**
 * Full-bleed ambient gradient behind the card stack. Colors cross-fade over
 * 600 ms as the top card changes; on API 31+ a RenderEffect blur melts the
 * stops together (below 31 the soft gradient alone reads fine — no bitmap
 * fallback on purpose).
 */
@Composable
fun PaletteBackdrop(palette: CardPalette, modifier: Modifier = Modifier) {
    val top by animateColorAsState(palette.top, tween(600), label = "backdrop-top")
    val mid by animateColorAsState(palette.mid, tween(600), label = "backdrop-mid")
    val blurModifier = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier
    Box(
        modifier = modifier
            .then(blurModifier)
            .background(
                Brush.verticalGradient(
                    0f to top,
                    0.55f to mid,
                    1f to SdzColor.Surface0,
                )
            ),
    )
}
