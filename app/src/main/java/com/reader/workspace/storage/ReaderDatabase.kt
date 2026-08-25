package com.reader.workspace.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reader.workspace.index.IndexDao
import com.reader.workspace.index.IndexEntryEntity
import com.reader.workspace.marginalia.MarginaliaDao
import com.reader.workspace.marginalia.MarginaliaItemEntity
import com.reader.workspace.marginalia.MarginaliaSettingsEntity
import com.reader.workspace.research.ResearchAxisEntity
import com.reader.workspace.research.ResearchDao
import com.reader.workspace.research.ResearchHistoryEntity
import com.reader.workspace.research.ResearchProfileEntity

@Database(
    entities = [
        VaultDocumentEntity::class,
        MarginaliaItemEntity::class,
        MarginaliaSettingsEntity::class,
        ResearchAxisEntity::class,
        ResearchProfileEntity::class,
        ResearchHistoryEntity::class,
        IndexEntryEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun vaultDocumentDao(): VaultDocumentDao
    abstract fun marginaliaDao(): MarginaliaDao
    abstract fun researchDao(): ResearchDao
    abstract fun indexDao(): IndexDao

    companion object {
        @Volatile
        private var instance: ReaderDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS marginalia_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        startOffset INTEGER,
                        endOffsetExclusive INTEGER,
                        kind TEXT NOT NULL,
                        xFraction REAL NOT NULL,
                        yFraction REAL NOT NULL,
                        widthFraction REAL NOT NULL,
                        heightFraction REAL NOT NULL,
                        zIndex INTEGER NOT NULL,
                        text TEXT,
                        assetId TEXT,
                        linkedDocumentId TEXT,
                        linkedPageIndex INTEGER,
                        createdAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_marginalia_items_documentId_pageIndex " +
                        "ON marginalia_items(documentId, pageIndex)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS marginalia_settings (
                        documentId TEXT NOT NULL PRIMARY KEY,
                        widthFraction REAL NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS research_axes (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        patternsEncoded TEXT NOT NULL,
                        matchMode TEXT NOT NULL,
                        caseSensitive INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        colorArgb INTEGER NOT NULL,
                        marker TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS research_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        axisIdsEncoded TEXT NOT NULL,
                        proximityChars INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS research_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        profileId TEXT,
                        axisIdsEncoded TEXT NOT NULL,
                        proximityChars INTEGER NOT NULL,
                        hitCount INTEGER NOT NULL,
                        intersectionCount INTEGER NOT NULL,
                        executedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_research_history_documentId_executedAtEpochMillis " +
                        "ON research_history(documentId, executedAtEpochMillis)",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE research_history ADD COLUMN scope TEXT NOT NULL DEFAULT 'PAGE'",
                )
                db.execSQL(
                    "ALTER TABLE research_history ADD COLUMN rangeStartPageIndex INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE research_history ADD COLUMN rangeEndPageIndex INTEGER",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE research_axes ADD COLUMN diacriticsSensitive INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE research_axes ADD COLUMN suffixMatch INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE research_profiles ADD COLUMN proximityScope TEXT NOT NULL DEFAULT 'CHARACTERS'",
                )
                db.execSQL(
                    "ALTER TABLE research_history ADD COLUMN proximityScope TEXT NOT NULL DEFAULT 'CHARACTERS'",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS index_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        startOffset INTEGER,
                        endOffsetExclusive INTEGER,
                        note TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_index_entries_documentId_pageIndex " +
                        "ON index_entries(documentId, pageIndex)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_index_entries_category " +
                        "ON index_entries(category)",
                )
            }
        }

        fun get(context: Context): ReaderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReaderDatabase::class.java,
                "reader.db",
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                .build()
                .also { instance = it }
        }
    }
}
