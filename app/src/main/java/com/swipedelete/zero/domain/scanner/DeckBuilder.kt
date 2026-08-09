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

    /** Any non-video this big is also worth surfacing (40 MB). */
    private val stillImageHeavyBytes = 40L * 1024 * 1024

    /** Hamming distance under which two dHashes are treated as near-duplicates. */
    private val duplicateHammingThreshold = 6

    data class ScanResult(
        val decks: List<Deck>,
        val comparisonDecks: Map<String, List<ComparisonPair>>,
        /**
         * Bytes held by files flagged as cleanup candidates, counted **once**
         * each.
         *
         * Summing every deck's bytes multi-counts badly: one large video is
         * simultaneously in its month deck, Heavy Hitters and Large Videos, so
         * the naive total can exceed the device's entire used space and read as
         * a fabricated number. Time Machine is excluded because it contains the
         * whole library by construction — including it would just report
         * "everything you own" as reclaimable.
         */
        val candidateBytes: Long = 0,
        /** Distinct files flagged as candidates. */
        val candidateCount: Int = 0,
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
            addAll(largeVideoDecks(items))
            addAll(clutterHotspotDecks(items))
            addAll(blurryDecks(items))
            addAll(screenshotsDecks(items))
        }
        val comparisons = duplicateComparisonDecks(items)
        val allDecks = decks + comparisons.keys.map { placeholderDeckFor(it, comparisons) }

        val candidates = allDecks
            .asSequence()
            .filter { it.kind != DeckKind.TIME_MACHINE }
            .flatMap { it.items.asSequence() }
            .distinctBy { it.contentUri }
            .toList()

        ScanResult(
            decks = allDecks,
            comparisonDecks = comparisons,
            candidateBytes = candidates.sumOf { it.sizeBytes },
            candidateCount = candidates.size,
        )
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
                    items = monthItems.sortedByDescending { it.dateAddedMillis },
                ) { chunk -> "${chunk.size} memories" }
            }
    }

    // --- Heavy Hitters --------------------------------------------------------

    private fun heavyHitterDecks(items: List<MediaItem>): List<Deck> {
        val heavy = items
            // Parenthesised deliberately: `&&` binds tighter than `||`, so the
            // unbracketed form collapsed to "any file ≥ 40 MB" and the video
            // threshold never applied at all.
            .filter { (it.isVideo && it.sizeBytes >= heavyVideoBytes) || it.sizeBytes >= stillImageHeavyBytes }
            .sortedByDescending { it.sizeBytes }
        return chunkIntoDecks(
            idPrefix = "heavy",
            kind = DeckKind.HEAVY_HITTERS,
            title = "Heavy Hitters",
            items = heavy,
        ) { "Biggest space hogs first" }
    }

    /** The "Large Videos" AI bucket: single files ≥1 GB, biggest first. */
    private fun largeVideoDecks(items: List<MediaItem>): List<Deck> {
        val huge = items
            .filter { it.isVideo && it.sizeBytes >= LARGE_VIDEO_BYTES }
            .sortedByDescending { it.sizeBytes }
        return chunkIntoDecks(
            idPrefix = LARGE_VIDEO_DECK_ID,
            kind = DeckKind.HEAVY_HITTERS,
            title = "Large Videos",
            items = huge,
        ) { "Videos over 1 GB — the fastest wins" }
    }

    /** The "Screenshots & Receipts" AI bucket (path + filename heuristics). */
    private fun screenshotsDecks(items: List<MediaItem>): List<Deck> {
        val matched = items
            .filter { isScreenshotOrReceipt(it.relativePath, it.displayName) }
            .sortedByDescending { it.dateAddedMillis }
        return chunkIntoDecks(
            idPrefix = "shots",
            kind = DeckKind.SCREENSHOTS,
            title = "Screenshots & Receipts",
            items = matched,
        ) { chunk -> "${chunk.size} shots & docs to triage" }
    }

    // --- Clutter Hotspots -----------------------------------------------------

    // "Hotspot" must mean a genuinely clutter-prone folder. Matching
    // "dcim/camera" swept in the entire camera roll, which then re-chunked into
    // hundreds of near-identical "Camera Bursts · Part N" decks — the whole
    // library duplicated under a misleading label, since Time Machine already
    // covers it.
    private val hotspotMatchers = linkedMapOf(
        "Screenshots" to listOf("screenshot"),
        "WhatsApp Media" to listOf("whatsapp"),
        "Telegram" to listOf("telegram"),
        "Camera Bursts" to listOf("burst"),
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
                items = matched,
            ) { chunk -> "${chunk.size} files in this hotspot" }
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
            items = blurry,
        ) { "Likely out-of-focus shots" }
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
            groupId = "duplicates",
            groupTitle = "Duplicates",
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
        items: List<MediaItem>,
        // Computed per chunk so a 238-item month split into parts of ≤50 never
        // claims "238 memories" on every part.
        subtitleFor: (List<MediaItem>) -> String,
    ): List<Deck> {
        if (items.isEmpty()) return emptyList()
        return items.chunked(MAX_DECK_SIZE).mapIndexed { index, chunk ->
            val suffix = if (items.size > MAX_DECK_SIZE) " · Part ${index + 1}" else ""
            Deck(
                id = if (index == 0) idPrefix else "$idPrefix:$index",
                kind = kind,
                title = title + suffix,
                subtitle = subtitleFor(chunk),
                items = chunk,
                groupId = idPrefix,
                groupTitle = title,
            )
        }
    }

    companion object {
        /** ≥1 GB marks a video for the "Large Videos" AI bucket. */
        const val LARGE_VIDEO_BYTES = 1L shl 30

        /** Deck-id prefix of the Large Videos bucket (also matched by the dashboard). */
        const val LARGE_VIDEO_DECK_ID = "heavy:xl"

        /** Pure heuristic shared with tests: screenshots, receipts, scans. */
        fun isScreenshotOrReceipt(relativePath: String?, displayName: String): Boolean {
            val path = (relativePath ?: "").lowercase(Locale.US)
            val name = displayName.lowercase(Locale.US)
            return path.contains("screenshot") ||
                name.startsWith("screenshot") ||
                name.contains("receipt") ||
                name.contains("invoice") ||
                name.contains("scan_")
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
