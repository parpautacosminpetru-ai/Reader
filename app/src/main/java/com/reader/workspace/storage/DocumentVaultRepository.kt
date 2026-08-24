package com.reader.workspace.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DocumentVaultRepository private constructor(
    private val context: Context,
    private val dao: VaultDocumentDao,
) {
    private val vaultDirectory: File = File(context.filesDir, "vault").apply {
        if (!exists() && !mkdirs()) {
            throw IOException("Unable to create local vault directory")
        }
    }

    val documents: Flow<List<VaultDocument>> = dao.observeAll().map { entities ->
        entities.map(VaultDocumentEntity::toModel)
    }

    suspend fun importDocument(uri: Uri): VaultDocument = withContext(Dispatchers.IO) {
        val metadata = readSourceMetadata(context.contentResolver, uri)
        val id = UUID.randomUUID().toString()
        val storedFileName = VaultFileNames.storedFileName(id, metadata.displayName)
        val destination = File(vaultDirectory, storedFileName)

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IOException("Unable to open selected document")
                destination.outputStream().use { output ->
                    input.copyTo(output, DEFAULT_COPY_BUFFER_SIZE)
                }
            }

            val entity = VaultDocumentEntity(
                id = id,
                displayName = metadata.displayName,
                storedFileName = storedFileName,
                mimeType = metadata.mimeType,
                sizeBytes = if (metadata.sizeBytes >= 0) metadata.sizeBytes else destination.length(),
                importedAtEpochMillis = System.currentTimeMillis(),
            )
            dao.insert(entity)
            entity.toModel()
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        val entity = dao.findById(id) ?: return@withContext
        val storedFile = File(vaultDirectory, entity.storedFileName)
        if (storedFile.exists() && !storedFile.delete()) {
            throw IOException("Unable to delete ${entity.displayName}")
        }
        dao.deleteById(id)
    }

    fun localFile(document: VaultDocument): File = File(vaultDirectory, document.storedFileName)

    companion object {
        @Volatile
        private var instance: DocumentVaultRepository? = null

        fun get(context: Context): DocumentVaultRepository = instance ?: synchronized(this) {
            instance ?: DocumentVaultRepository(
                context = context.applicationContext,
                dao = ReaderDatabase.get(context).vaultDocumentDao(),
            ).also { instance = it }
        }
    }
}

private data class SourceMetadata(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

private fun readSourceMetadata(resolver: ContentResolver, uri: Uri): SourceMetadata {
    var displayName = "document"
    var sizeBytes = -1L

    resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                displayName = cursor.getString(nameIndex).ifBlank { "document" }
            }

            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                sizeBytes = cursor.getLong(sizeIndex)
            }
        }
    }

    return SourceMetadata(
        displayName = displayName,
        mimeType = resolver.getType(uri) ?: "application/octet-stream",
        sizeBytes = sizeBytes,
    )
}
