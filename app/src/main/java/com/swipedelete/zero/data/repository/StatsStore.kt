package com.swipedelete.zero.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statsDataStore by preferencesDataStore(name = "sdz_stats")

/**
 * Tiny DataStore-backed stats ledger. Currently a single number: the lifetime
 * bytes actually reclaimed ("14.2 GB Reclaimed" in the staging sheet) —
 * incremented only with bytes whose deletion the purge engine verified, never
 * with optimistic staged totals.
 */
@Singleton
class StatsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val reclaimedKey = longPreferencesKey("lifetime_reclaimed_bytes")

    val lifetimeReclaimedBytes: Flow<Long> =
        context.statsDataStore.data.map { it[reclaimedKey] ?: 0L }

    suspend fun addReclaimed(bytes: Long) {
        if (bytes <= 0) return
        context.statsDataStore.edit { prefs ->
            prefs[reclaimedKey] = (prefs[reclaimedKey] ?: 0L) + bytes
        }
    }
}
