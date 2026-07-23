package com.swipedelete.zero.domain.scanner

import com.swipedelete.zero.data.local.ExclusionDao
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.domain.algorithm.PerceptualHasher
import com.swipedelete.zero.domain.model.ComparisonPair
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.DeckKind
import com.swipedelete.zero.domain.model.MAX_DECK_SIZE
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Transforms the raw MediaStore scan into the four families of ≤50-item decks.
 *
 * All grouping runs on [Dispatchers.Default] and respects the Exclusion Vault:
 * any item whose perceptual hash or folder path is excluded is filtered out
 * (`WHERE hash NOT IN (SELECT hash FROM exclusions)` in spirit).
 */
@Singleton
class DeckBuilder @Inject constructor(
    private val mediaStore: MediaStoreRepository,
    private val analysisDao: MediaAnalysisDao,
    private val exclusionDao: ExclusionDao,
) {

    /** Large-video threshold for Heavy Hitters (100 MB). */
    private val heavyVideoBytes = 100L * 1024 * 1024

    /** Hamming distance under which two dHashes are treated as near-duplicates. */
    private val duplicateHammingThreshold = 6

    data class ScanResult(
        val decks: List<Deck>,
        val comparisonDecks: Map<String, List<ComparisonPair>>,
    )

    suspend fun buildAll(): ScanResult = withContext(Dispatchers.Default) {
        val excludedHashes = exclusionDao.excludedHashes().toHashSet()
        val excludedFolders = exclusionDao.excludedFolders()

        val analysisById = analysisDao.allByHash().associateBy { it.mediaId }

        val raw = mediaStore.queryVisualMedia()
        val items = raw
            .filter { item -> !isExcluded(item, excludedHashes, excludedFolders) }
            .map { item ->
                val a = analysisById[item.id]
                if (a != null) {
                    item.copy(
                        perceptualHash = a.dHash,
                        sharpnessScore = a.sharpnessVariance,
                    )
                } else item
            }

        val decks = buildList {
            addAll(timeMachineDecks(items))
            addAll(heavyHitterDecks(items))
            addAll(clutterHotspotDecks(items))
            addAll(blurryDecks(items))
        }
        val comparisons = duplicateComparisonDecks(items)

        ScanResult(decks = decks + comparisons.keys.map { placeholderDeckFor(it, comparisons) },
            comparisonDecks = comparisons)
    }

    // --- Time Machine ---------------------------------------------------------

    private fun timeMachineDecks(items: List<MediaItem>): List<Deck> {
        val byMonth = items.groupBy { monthKey(it.dateAddedMillis) }
        return byMonth.entries
            .sortedByDescending { it.key }
            .flatMap { (key, monthItems) ->
                chunkIntoDecks(
                    idPrefix = "time:$key",
                    kind = DeckKind.TIME_MACHINE,
                    title = monthLabel(key),
                    subtitle = "${monthItems.size} memories",
                    items = monthItems.sortedByDescending { it.dateAddedMillis },
                )
            }
    }

    // --- Heavy Hitters --------------------------------------------------------

    private fun heavyHitterDecks(items: List<MediaItem>): List<Deck> {
        val heavy = items
            .filter { it.isVideo && it.sizeBytes >= heavyVideoBytes || it.sizeBytes >= 40L * 1024 * 1024 }
            .sortedByDescending { it.sizeBytes }
        return chunkIntoDecks(
            idPrefix = "heavy",
            kind = DeckKind.HEAVY_HITTERS,
            title = "Heavy Hitters",
            subtitle = "Biggest space hogs first",
            items = heavy,
        )
    }

    // --- Clutter Hotspots -----------------------------------------------------

    private val hotspotMatchers = linkedMapOf(
        "Screenshots" to listOf("screenshot"),
        "WhatsApp Media" to listOf("whatsapp"),
        "Telegram" to listOf("telegram"),
        "Camera Bursts" to listOf("burst", "dcim/camera"),
        "Downloads" to listOf("download"),
    )

    private fun clutterHotspotDecks(items: List<MediaItem>): List<Deck> =
        hotspotMatchers.entries.flatMap { (label, needles) ->
            val matched = items.filter { item ->
                val path = (item.relativePath ?: "").lowercase(Locale.US)
                needles.any { path.contains(it) }
            }.sortedByDescending { it.dateAddedMillis }
            chunkIntoDecks(
                idPrefix = "hotspot:${label.lowercase(Locale.US).replace(' ', '_')}",
                kind = DeckKind.CLUTTER_HOTSPOT,
                title = label,
                subtitle = "${matched.size} files in this hotspot",
                items = matched,
            )
        }

    // --- Blurry ---------------------------------------------------------------

    private fun blurryDecks(items: List<MediaItem>): List<Deck> {
        val blurry = items
            .filter { it.sharpnessScore != null && it.sharpnessScore < com.swipedelete.zero.domain.algorithm.BlurDetector.DEFAULT_BLUR_THRESHOLD }
            .sortedBy { it.sharpnessScore }
        return chunkIntoDecks(
            idPrefix = "blurry",
            kind = DeckKind.BLURRY,
            title = "Blurry & Soft",
            subtitle = "Likely out-of-focus shots",
            items = blurry,
        )
    }

    // --- Duplicates (comparison pairs) ---------------------------------------

    private fun duplicateComparisonDecks(
        items: List<MediaItem>,
    ): Map<String, List<ComparisonPair>> {
        val hashed = items.filter { it.perceptualHash != null }
        val used = HashSet<Long>()
        val pairs = ArrayList<ComparisonPair>()

        // Simple near-duplicate clustering by dHash Hamming distance. For a
        // production dataset a BK-tree would replace this O(n²) sweep; here it is
        // bounded because it runs off the pre-filtered hashed set.
        for (i in hashed.indices) {
            val a = hashed[i]
            if (a.id in used) continue
            for (j in i + 1 until hashed.size) {
                val b = hashed[j]
                if (b.id in used) continue
                val dist = PerceptualHasher.hammingDistance(
                    a.perceptualHash!!, b.perceptualHash!!,
                )
                if (dist <= duplicateHammingThreshold) {
                    // Keeper = sharper, else higher-res, else larger file.
                    val primary = pickKeeper(a, b)
                    val secondary = if (primary === a) b else a
                    pairs += ComparisonPair(primary, secondary)
                    used += a.id
                    used += b.id
                    break
                }
            }
        }
        if (pairs.isEmpty()) return emptyMap()
        // Chunk into ≤50-pair comparison decks.
        return pairs.chunked(MAX_DECK_SIZE).mapIndexed { idx, chunk ->
            "duplicates:$idx" to chunk
        }.toMap()
    }

    private fun pickKeeper(a: MediaItem, b: MediaItem): MediaItem {
        val sa = a.sharpnessScore
        val sb = b.sharpnessScore
        if (sa != null && sb != null && abs(sa - sb) > 5.0) return if (sa > sb) a else b
        if (a.megapixels != b.megapixels) return if (a.megapixels > b.megapixels) a else b
        return if (a.sizeBytes >= b.sizeBytes) a else b
    }

    private fun placeholderDeckFor(
        id: String,
        comparisons: Map<String, List<ComparisonPair>>,
    ): Deck {
        val pairs = comparisons[id].orEmpty()
        val items = pairs.flatMap { listOf(it.primary, it.secondary) }
        return Deck(
            id = id,
            kind = DeckKind.DUPLICATES,
            title = "Duplicates",
            subtitle = "${pairs.size} sets to compare",
            items = items,
        )
    }

    // --- Helpers --------------------------------------------------------------

    private fun isExcluded(
        item: MediaItem,
        excludedHashes: Set<Long>,
        excludedFolders: List<String>,
    ): Boolean {
        if (item.perceptualHash != null && item.perceptualHash in excludedHashes) return true
        val path = item.relativePath ?: return false
        return excludedFolders.any { folder -> path.contains(folder, ignoreCase = true) }
    }

    private fun chunkIntoDecks(
        idPrefix: String,
        kind: DeckKind,
        title: String,
        subtitle: String,
        items: List<MediaItem>,
    ): List<Deck> {
        if (items.isEmpty()) return emptyList()
        return items.chunked(MAX_DECK_SIZE).mapIndexed { index, chunk ->
            val suffix = if (items.size > MAX_DECK_SIZE) " · Part ${index + 1}" else ""
            Deck(
                id = if (index == 0) idPrefix else "$idPrefix:$index",
                kind = kind,
                title = title + suffix,
                subtitle = subtitle,
                items = chunk,
            )
        }
    }

    private fun monthKey(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
    }

    private fun monthLabel(key: Int): String {
        val year = key / 100
        val month = key % 100
        val name = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        ).getOrElse(month) { "" }
        return "$name $year"
    }
}
