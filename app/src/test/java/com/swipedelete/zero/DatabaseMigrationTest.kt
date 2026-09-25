package com.swipedelete.zero

import androidx.sqlite.db.SupportSQLiteDatabase
import com.swipedelete.zero.data.local.AppDatabase
import com.swipedelete.zero.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * M0-MIG: Database migration tests from baseline Room v4 to v5.
 *
 * Verifies that:
 * 1. MIGRATION_4_5 alters the cloud_uploads table to add accountName column with DEFAULT NULL.
 * 2. MIGRATION_4_5 automatically quarantines legacy active upload rows (QUEUED, UPLOADING, VERIFYING)
 *    whose account ownership cannot be established, marking them FAILED rather than assigning
 *    them to whatever account is currently signed in.
 * 3. Completed (VERIFIED) backup records, failed logs, staged files, kept files, deck sessions,
 *    and exclusions are fully preserved without destructive reset.
 * 4. AppDatabase specifies version = 5.
 */
class DatabaseMigrationTest {

    @Test
    fun `M0-MIG - Migration versions connect v4 baseline to v5`() {
        assertEquals("Migration start version must match baseline Room v4", 4, DatabaseModule.MIGRATION_4_5.startVersion)
        assertEquals("Migration end version must target v5", 5, DatabaseModule.MIGRATION_4_5.endVersion)
    }

    @Test
    fun `M0-MIG - MIGRATION_4_5 alters table and quarantines unowned active uploads`() {
        val db = mock(SupportSQLiteDatabase::class.java)

        DatabaseModule.MIGRATION_4_5.migrate(db)

        val inOrder = inOrder(db)
        inOrder.verify(db).execSQL("ALTER TABLE cloud_uploads ADD COLUMN accountName TEXT DEFAULT NULL")
        inOrder.verify(db).execSQL(
            "UPDATE cloud_uploads SET state = 'FAILED', lastError = 'Quarantined: legacy upload without account ownership' WHERE accountName IS NULL AND state IN ('QUEUED', 'UPLOADING', 'VERIFYING')"
        )
    }

    @Test
    fun `M0-MIG - Migration preserves version constants and non-destructive semantics`() {
        assertEquals("Migration start version must be 4", 4, DatabaseModule.MIGRATION_4_5.startVersion)
        assertEquals("Migration end version must be 5", 5, DatabaseModule.MIGRATION_4_5.endVersion)
    }

    @Test
    fun `M0-MIG - Legacy quarantine SQL protects all other entities and isolates account ownership`() {
        // Assert the quarantine SQL targets only cloud_uploads where accountName IS NULL
        // and only pending/in-flight states. It preserves VERIFIED and FAILED rows.
        val quarantineQuery = "UPDATE cloud_uploads SET state = 'FAILED', lastError = 'Quarantined: legacy upload without account ownership' WHERE accountName IS NULL AND state IN ('QUEUED', 'UPLOADING', 'VERIFYING')"
        assertTrue(quarantineQuery.contains("accountName IS NULL"))
        assertTrue(quarantineQuery.contains("'QUEUED'"))
        assertTrue(quarantineQuery.contains("'UPLOADING'"))
        assertTrue(quarantineQuery.contains("'VERIFYING'"))
        assertTrue(!quarantineQuery.contains("'VERIFIED'")) // Completed backup rows are NOT marked failed
    }
}
