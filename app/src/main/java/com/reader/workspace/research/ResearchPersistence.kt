package com.reader.workspace.research

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.reader.workspace.storage.ReaderDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "research_axes")
data class ResearchAxisEntity(
    @PrimaryKey val id: String,
    val title: String,
    val patternsEncoded: String,
    val matchMode: String,
    val caseSensitive: Boolean,
    val enabled: Boolean,
    val colorArgb: Long,
    val marker: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "research_profiles")
data class ResearchProfileEntity(
    @PrimaryKey val id: String,
    val title: String,
    val axisIdsEncoded: String,
    val proximityChars: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "research_history",
    indices = [Index(value = ["documentId", "executedAtEpochMillis"])],
)
data class ResearchHistoryEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val profileId: String?,
    val axisIdsEncoded: String,
    val proximityChars: Int,
    val hitCount: Int,
    val intersectionCount: Int,
    @ColumnInfo(defaultValue = "'PAGE'") val scope: String,
    val rangeStartPageIndex: Int?,
    val rangeEndPageIndex: Int?,
    val executedAtEpochMillis: Long,
)

@Dao
interface ResearchDao {
    @Query("SELECT * FROM research_axes ORDER BY title COLLATE NOCASE ASC, createdAtEpochMillis ASC")
    fun observeAxes(): Flow<List<ResearchAxisEntity>>

    @Query("SELECT * FROM research_profiles ORDER BY title COLLATE NOCASE ASC, createdAtEpochMillis ASC")
    fun observeProfiles(): Flow<List<ResearchProfileEntity>>

    @Query(
        "SELECT * FROM research_history WHERE documentId = :documentId " +
            "ORDER BY executedAtEpochMillis DESC LIMIT :limit",
    )
    fun observeHistory(documentId: String, limit: Int = 50): Flow<List<ResearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAxis(axis: ResearchAxisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ResearchProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: ResearchHistoryEntity)

    @Query("DELETE FROM research_axes WHERE id = :id")
    suspend fun deleteAxis(id: String)

    @Query("DELETE FROM research_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("DELETE FROM research_history WHERE documentId = :documentId")
    suspend fun deleteHistoryForDocument(documentId: String)
}

data class ResearchAxisDefinition(
    val id: String,
    val title: String,
    val patterns: List<String>,
    val matchMode: LexicalMatchMode,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    val colorArgb: Long = ResearchPalette.DEFAULT_COLORS.first(),
    val marker: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun toLexicalAxis(): LexicalAxis = LexicalAxis(
        id = id,
        title = title,
        patterns = patterns,
        matchMode = matchMode,
        caseSensitive = caseSensitive,
        enabled = enabled,
    )
}

data class ResearchProfile(
    val id: String,
    val title: String,
    val axisIds: List<String>,
    val proximityChars: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

enum class ResearchHistoryScope {
    PAGE,
    RANGE,
    DOCUMENT,
}

data class ResearchHistoryEntry(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val profileId: String?,
    val axisIds: List<String>,
    val proximityChars: Int,
    val hitCount: Int,
    val intersectionCount: Int,
    val executedAtEpochMillis: Long,
    val scope: ResearchHistoryScope = ResearchHistoryScope.PAGE,
    val rangeStartPageIndex: Int? = null,
    val rangeEndPageIndex: Int? = null,
) {
    fun scopeLabel(): String = when (scope) {
        ResearchHistoryScope.PAGE -> "page ${pageIndex + 1}"
        ResearchHistoryScope.RANGE -> {
            val start = (rangeStartPageIndex ?: pageIndex) + 1
            val end = (rangeEndPageIndex ?: rangeStartPageIndex ?: pageIndex) + 1
            "pages $start–$end"
        }
        ResearchHistoryScope.DOCUMENT -> "whole document"
    }
}

class ResearchRepository private constructor(
    private val dao: ResearchDao,
) {
    val axes: Flow<List<ResearchAxisDefinition>> = dao.observeAxes().map { entities ->
        entities.map(ResearchAxisEntity::toModel)
    }

    val profiles: Flow<List<ResearchProfile>> = dao.observeProfiles().map { entities ->
        entities.map(ResearchProfileEntity::toModel)
    }

    fun history(documentId: String): Flow<List<ResearchHistoryEntry>> =
        dao.observeHistory(documentId).map { entities -> entities.map(ResearchHistoryEntity::toModel) }

    suspend fun saveAxis(axis: ResearchAxisDefinition) {
        dao.upsertAxis(axis.toEntity())
    }

    suspend fun deleteAxis(id: String) {
        dao.deleteAxis(id)
    }

    suspend fun saveProfile(profile: ResearchProfile) {
        dao.upsertProfile(profile.toEntity())
    }

    suspend fun deleteProfile(id: String) {
        dao.deleteProfile(id)
    }

    suspend fun recordHistory(entry: ResearchHistoryEntry) {
        dao.insertHistory(entry.toEntity())
    }

    suspend fun deleteHistoryForDocument(documentId: String) {
        dao.deleteHistoryForDocument(documentId)
    }

    companion object {
        @Volatile
        private var instance: ResearchRepository? = null

        fun get(context: Context): ResearchRepository = instance ?: synchronized(this) {
            instance ?: ResearchRepository(
                ReaderDatabase.get(context.applicationContext).researchDao(),
            ).also { instance = it }
        }
    }
}

object ResearchPalette {
    val DEFAULT_COLORS: List<Long> = listOf(
        0xFFE53935L,
        0xFF1E88E5L,
        0xFF43A047L,
        0xFFF9A825L,
        0xFF8E24AAL,
        0xFF00897BL,
        0xFF6D4C41L,
        0xFFD81B60L,
    )

    fun colorFor(index: Int): Long = DEFAULT_COLORS[Math.floorMod(index, DEFAULT_COLORS.size)]
}

object ResearchCodec {
    fun encode(values: List<String>): String = buildString {
        values.forEach { value ->
            append(value.length)
            append(':')
            append(value)
        }
    }

    fun decode(encoded: String): List<String> {
        if (encoded.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var cursor = 0

        while (cursor < encoded.length) {
            val colon = encoded.indexOf(':', startIndex = cursor)
            if (colon <= cursor) return emptyList()
            val length = encoded.substring(cursor, colon).toIntOrNull() ?: return emptyList()
            if (length < 0) return emptyList()
            val start = colon + 1
            val end = start + length
            if (end > encoded.length) return emptyList()
            result += encoded.substring(start, end)
            cursor = end
        }
        return result
    }
}

fun ResearchAxisEntity.toModel(): ResearchAxisDefinition = ResearchAxisDefinition(
    id = id,
    title = title,
    patterns = ResearchCodec.decode(patternsEncoded),
    matchMode = runCatching { LexicalMatchMode.valueOf(matchMode) }.getOrDefault(LexicalMatchMode.PREFIX),
    caseSensitive = caseSensitive,
    enabled = enabled,
    colorArgb = colorArgb,
    marker = marker,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ResearchAxisDefinition.toEntity(): ResearchAxisEntity = ResearchAxisEntity(
    id = id,
    title = title,
    patternsEncoded = ResearchCodec.encode(patterns),
    matchMode = matchMode.name,
    caseSensitive = caseSensitive,
    enabled = enabled,
    colorArgb = colorArgb,
    marker = marker,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ResearchProfileEntity.toModel(): ResearchProfile = ResearchProfile(
    id = id,
    title = title,
    axisIds = ResearchCodec.decode(axisIdsEncoded),
    proximityChars = proximityChars.coerceAtLeast(0),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ResearchProfile.toEntity(): ResearchProfileEntity = ResearchProfileEntity(
    id = id,
    title = title,
    axisIdsEncoded = ResearchCodec.encode(axisIds),
    proximityChars = proximityChars.coerceAtLeast(0),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ResearchHistoryEntity.toModel(): ResearchHistoryEntry = ResearchHistoryEntry(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    profileId = profileId,
    axisIds = ResearchCodec.decode(axisIdsEncoded),
    proximityChars = proximityChars.coerceAtLeast(0),
    hitCount = hitCount.coerceAtLeast(0),
    intersectionCount = intersectionCount.coerceAtLeast(0),
    executedAtEpochMillis = executedAtEpochMillis,
    scope = runCatching { ResearchHistoryScope.valueOf(scope) }.getOrDefault(ResearchHistoryScope.PAGE),
    rangeStartPageIndex = rangeStartPageIndex,
    rangeEndPageIndex = rangeEndPageIndex,
)

fun ResearchHistoryEntry.toEntity(): ResearchHistoryEntity = ResearchHistoryEntity(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    profileId = profileId,
    axisIdsEncoded = ResearchCodec.encode(axisIds),
    proximityChars = proximityChars.coerceAtLeast(0),
    hitCount = hitCount.coerceAtLeast(0),
    intersectionCount = intersectionCount.coerceAtLeast(0),
    scope = scope.name,
    rangeStartPageIndex = rangeStartPageIndex,
    rangeEndPageIndex = rangeEndPageIndex,
    executedAtEpochMillis = executedAtEpochMillis,
)
