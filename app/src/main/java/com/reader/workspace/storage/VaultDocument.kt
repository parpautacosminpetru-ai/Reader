package com.reader.workspace.storage

data class VaultDocument(
    val id: String,
    val displayName: String,
    val storedFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val importedAtEpochMillis: Long,
)

object VaultFileNames {
    fun extensionFrom(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        if (dot <= 0 || dot == displayName.lastIndex) return ""

        return displayName
            .substring(dot + 1)
            .lowercase()
            .filter { it.isLetterOrDigit() }
            .take(12)
    }

    fun storedFileName(id: String, displayName: String): String {
        val extension = extensionFrom(displayName)
        return if (extension.isBlank()) id else "$id.$extension"
    }
}

object VaultDisplay {
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "Unknown size"
        if (bytes < 1_024) return "$bytes B"

        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1_024 && unitIndex < units.lastIndex) {
            value /= 1_024
            unitIndex += 1
        }

        return if (value >= 10 || value % 1.0 == 0.0) {
            "%.0f %s".format(value, units[unitIndex])
        } else {
            "%.1f %s".format(value, units[unitIndex])
        }
    }

    fun typeLabel(document: VaultDocument): String {
        val extension = VaultFileNames.extensionFrom(document.displayName)
        return when {
            extension.isNotBlank() -> extension.uppercase()
            document.mimeType.isNotBlank() -> document.mimeType
            else -> "FILE"
        }
    }
}
