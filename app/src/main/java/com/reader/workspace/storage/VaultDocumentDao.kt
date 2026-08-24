package com.reader.workspace.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDocumentDao {
    @Query("SELECT * FROM vault_documents ORDER BY importedAtEpochMillis DESC")
    fun observeAll(): Flow<List<VaultDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: VaultDocumentEntity)

    @Query("SELECT * FROM vault_documents WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): VaultDocumentEntity?

    @Query("DELETE FROM vault_documents WHERE id = :id")
    suspend fun deleteById(id: String)
}
