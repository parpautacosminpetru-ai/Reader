package com.reader.workspace.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_documents",
    indices = [Index(value = ["storedFileName"], unique = true)],
)
data class VaultDocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val storedFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val importedAtEpochMillis: Long,
)

fun VaultDocumentEntity.toModel(): VaultDocument = VaultDocument(
    id = id,
    displayName = displayName,
    storedFileName = storedFileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    importedAtEpochMillis = importedAtEpochMillis,
)
