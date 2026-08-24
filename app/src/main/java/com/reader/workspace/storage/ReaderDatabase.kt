package com.reader.workspace.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VaultDocumentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun vaultDocumentDao(): VaultDocumentDao

    companion object {
        @Volatile
        private var instance: ReaderDatabase? = null

        fun get(context: Context): ReaderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReaderDatabase::class.java,
                "reader.db",
            ).build().also { instance = it }
        }
    }
}
