package com.swipedelete.zero.data.repository

import com.swipedelete.zero.data.local.ExclusionDao
import com.swipedelete.zero.data.local.ExclusionEntity
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Exclusion Vault. Starred items and excluded folders live here and are
 * filtered out of every future scan (see [com.swipedelete.zero.domain.scanner
 * .DeckBuilder]). Exposed to the Settings screen for review/removal.
 */
@Singleton
class ExclusionRepository @Inject constructor(
    private val dao: ExclusionDao,
) {
    fun observeAll(): Flow<List<ExclusionEntity>> = dao.observeAll()

    /** Up-swipe: star an item so it never surfaces in a deck again. */
    suspend fun starItem(item: MediaItem) {
        dao.add(
            ExclusionEntity(
                type = ExclusionEntity.TYPE_STARRED_FILE,
                uri = item.contentUri.toString(),
                perceptualHash = item.perceptualHash,
                folderPath = null,
                label = item.displayName,
                createdAtMillis = 0L,
            )
        )
    }

    suspend fun excludeFolder(path: String, label: String) {
        dao.add(
            ExclusionEntity(
                type = ExclusionEntity.TYPE_EXCLUDED_FOLDER,
                uri = null,
                perceptualHash = null,
                folderPath = path,
                label = label,
                createdAtMillis = 0L,
            )
        )
    }

    suspend fun remove(id: Long) = dao.remove(id)
}
