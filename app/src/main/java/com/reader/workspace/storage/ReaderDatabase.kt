package com.reader.workspace.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reader.workspace.marginalia.MarginaliaDao
import com.reader.workspace.marginalia.MarginaliaItemEntity
import com.reader.workspace.marginalia.MarginaliaSettingsEntity

@Database(
    entities = [
        VaultDocumentEntity::class,
        MarginaliaItemEntity::class,
        MarginaliaSettingsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun vaultDocumentDao(): VaultDocumentDao
    abstract fun marginaliaDao(): MarginaliaDao

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

        fun get(context: Context): ReaderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReaderDatabase::class.java,
                "reader.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
