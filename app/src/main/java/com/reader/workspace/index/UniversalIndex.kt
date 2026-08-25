package com.reader.workspace.index

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.reader.workspace.marginalia.MarginaliaItem
import com.reader.workspace.marginalia.MarginaliaItemKind
import com.reader.workspace.storage.ReaderDatabase
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(
    tableName = "index_entries",
    indices = [
        Index(value = ["documentId", "pageIndex"]),
        Index(value = ["category"]),
    ],
)
data class IndexEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val documentId: String,
    val pageIndex: Int,
    val startOffset: Int?,
    val endOffsetExclusive: Int?,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Dao
interface IndexDao {
    @Query(
        "SELECT * FROM index_entries " +
            "ORDER BY title COLLATE NOCASE ASC, category COLLATE NOCASE ASC, createdAtEpochMillis ASC",
    )
    fun observeAll(): Flow<List<IndexEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IndexEntryEntity)

    @Query("DELETE FROM index_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM index_entries WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)
}

enum class UniversalIndexSource {
    MANUAL,
    RESEARCH,
    MARGINALIA,
}

data class IndexEntry(
    val id: String,
    val title: String,
    val category: String,
    val documentId: String,
    val pageIndex: Int,
    val startOffset: Int? = null,
    val endOffsetExclusive: Int? = null,
    val note: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class UniversalIndexItem(
    val id: String,
    val title: String,
    val category: String,
    val documentId: String,
    val pageIndex: Int,
    val startOffset: Int?,
    val endOffsetExclusive: Int?,
    val note: String?,
    val source: UniversalIndexSource,
)

class IndexRepository private constructor(
    private val dao: IndexDao,
) {
    val entries: Flow<List<IndexEntry>> = dao.observeAll().map { entities ->
        entities.map(IndexEntryEntity::toModel)
    }

    suspend fun save(entry: IndexEntry) {
        dao.upsert(entry.normalized().toEntity())
    }

    suspend fun delete(id: String) {
        dao.delete(id)
    }

    suspend fun deleteForDocument(documentId: String) {
        dao.deleteForDocument(documentId)
    }

    companion object {
        @Volatile
        private var instance: IndexRepository? = null

        fun get(context: Context): IndexRepository = instance ?: synchronized(this) {
            instance ?: IndexRepository(
                ReaderDatabase.get(context.applicationContext).indexDao(),
            ).also { instance = it }
        }
    }
}

object UniversalIndexComposer {
    fun compose(
        manualEntries: List<IndexEntry>,
        marginaliaItems: List<MarginaliaItem>,
    ): List<UniversalIndexItem> {
        val manual = manualEntries.map { entry ->
            UniversalIndexItem(
                id = "manual:${entry.id}",
                title = entry.title,
                category = entry.category,
                documentId = entry.documentId,
                pageIndex = entry.pageIndex,
                startOffset = entry.startOffset,
                endOffsetExclusive = entry.endOffsetExclusive,
                note = entry.note,
                source = UniversalIndexSource.MANUAL,
            )
        }

        val derived = marginaliaItems
            .asSequence()
            .filter { item -> !item.text.isNullOrBlank() }
            .map { item ->
                val isResearch = item.kind == MarginaliaItemKind.RESEARCH_LINK
                UniversalIndexItem(
                    id = "marginalia:${item.id}",
                    title = derivedTitle(item.text.orEmpty()),
                    category = if (isResearch) "Research" else "Marginalia",
                    documentId = item.anchor.documentId,
                    pageIndex = item.anchor.pageIndex,
                    startOffset = item.anchor.startOffset,
                    endOffsetExclusive = item.anchor.endOffsetExclusive,
                    note = item.text,
                    source = if (isResearch) UniversalIndexSource.RESEARCH else UniversalIndexSource.MARGINALIA,
                )
            }
            .toList()

        return (manual + derived).sortedWith(
            compareBy<UniversalIndexItem> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.category.lowercase(Locale.ROOT) }
                .thenBy(UniversalIndexItem::pageIndex),
        )
    }

    fun filter(
        items: List<UniversalIndexItem>,
        query: String,
        category: String?,
    ): List<UniversalIndexItem> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val normalizedCategory = category?.trim()?.takeIf(String::isNotEmpty)
        return items.filter { item ->
            val categoryMatches = normalizedCategory == null ||
                item.category.equals(normalizedCategory, ignoreCase = true)
            val queryMatches = normalizedQuery.isEmpty() ||
                item.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                item.category.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                item.note.orEmpty().lowercase(Locale.ROOT).contains(normalizedQuery)
            categoryMatches && queryMatches
        }
    }

    fun alphabetBucket(title: String): String {
        val first = title.trim().firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    private fun derivedTitle(text: String): String {
        val compact = text
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (compact.isEmpty()) return "Untitled annotation"
        return if (compact.length <= 72) compact else compact.take(69).trimEnd() + "…"
    }
}

fun IndexEntry.normalized(): IndexEntry {
    val safeStart = startOffset?.coerceAtLeast(0)
    val safeEnd = endOffsetExclusive?.let { end ->
        if (safeStart == null) end.coerceAtLeast(0) else end.coerceAtLeast(safeStart)
    }
    return copy(
        title = title.trim().ifBlank { "Untitled" },
        category = category.trim().ifBlank { "General" },
        pageIndex = pageIndex.coerceAtLeast(0),
        startOffset = safeStart,
        endOffsetExclusive = safeEnd,
        note = note?.trim()?.takeIf(String::isNotEmpty),
    )
}

fun IndexEntry.toEntity(): IndexEntryEntity = IndexEntryEntity(
    id = id,
    title = title,
    category = category,
    documentId = documentId,
    pageIndex = pageIndex,
    startOffset = startOffset,
    endOffsetExclusive = endOffsetExclusive,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun IndexEntryEntity.toModel(): IndexEntry = IndexEntry(
    id = id,
    title = title,
    category = category,
    documentId = documentId,
    pageIndex = pageIndex,
    startOffset = startOffset,
    endOffsetExclusive = endOffsetExclusive,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
