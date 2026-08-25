package com.reader.workspace.marginalia

import android.content.Context
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

@Entity(
    tableName = "marginalia_items",
    indices = [Index(value = ["documentId", "pageIndex"])],
)
data class MarginaliaItemEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val startOffset: Int?,
    val endOffsetExclusive: Int?,
    val kind: String,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val zIndex: Int,
    val text: String?,
    val assetId: String?,
    val linkedDocumentId: String?,
    val linkedPageIndex: Int?,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "marginalia_settings")
data class MarginaliaSettingsEntity(
    @PrimaryKey val documentId: String,
    val widthFraction: Float,
)

@Dao
interface MarginaliaDao {
    @Query(
        "SELECT * FROM marginalia_items WHERE documentId = :documentId " +
            "ORDER BY pageIndex ASC, zIndex ASC, createdAtEpochMillis ASC",
    )
    fun observeItems(documentId: String): Flow<List<MarginaliaItemEntity>>

    @Query(
        "SELECT * FROM marginalia_items WHERE text IS NOT NULL AND TRIM(text) != '' " +
            "ORDER BY documentId ASC, pageIndex ASC, createdAtEpochMillis ASC",
    )
    fun observeIndexableItems(): Flow<List<MarginaliaItemEntity>>

    @Query("SELECT * FROM marginalia_settings WHERE documentId = :documentId LIMIT 1")
    fun observeSettings(documentId: String): Flow<MarginaliaSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: MarginaliaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: MarginaliaSettingsEntity)

    @Query("DELETE FROM marginalia_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM marginalia_items WHERE documentId = :documentId")
    suspend fun deleteItemsForDocument(documentId: String)

    @Query("DELETE FROM marginalia_settings WHERE documentId = :documentId")
    suspend fun deleteSettingsForDocument(documentId: String)
}

class MarginaliaRepository private constructor(
    private val dao: MarginaliaDao,
) {
    fun observeItems(documentId: String): Flow<List<MarginaliaItem>> =
        dao.observeItems(documentId).map { entities -> entities.map(MarginaliaItemEntity::toModel) }

    val indexableItems: Flow<List<MarginaliaItem>> =
        dao.observeIndexableItems().map { entities -> entities.map(MarginaliaItemEntity::toModel) }

    fun observeWidth(documentId: String): Flow<Float> =
        dao.observeSettings(documentId).map { entity ->
            MarginaliaGeometry.clampWidthFraction(
                entity?.widthFraction ?: MarginaliaGeometry.DEFAULT_WIDTH_FRACTION,
            )
        }

    suspend fun saveItem(item: MarginaliaItem) {
        dao.upsertItem(MarginaliaGeometry.normalize(item).toEntity())
    }

    suspend fun deleteItem(id: String) {
        dao.deleteItem(id)
    }

    suspend fun saveWidth(documentId: String, widthFraction: Float) {
        dao.upsertSettings(
            MarginaliaSettingsEntity(
                documentId = documentId,
                widthFraction = MarginaliaGeometry.clampWidthFraction(widthFraction),
            ),
        )
    }

    suspend fun deleteForDocument(documentId: String) {
        dao.deleteItemsForDocument(documentId)
        dao.deleteSettingsForDocument(documentId)
    }

    companion object {
        @Volatile
        private var instance: MarginaliaRepository? = null

        fun get(context: Context): MarginaliaRepository = instance ?: synchronized(this) {
            instance ?: MarginaliaRepository(
                ReaderDatabase.get(context.applicationContext).marginaliaDao(),
            ).also { instance = it }
        }
    }
}

fun MarginaliaItemEntity.toModel(): MarginaliaItem = MarginaliaItem(
    id = id,
    anchor = DocumentAnchor(
        documentId = documentId,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffsetExclusive = endOffsetExclusive,
    ),
    kind = runCatching { MarginaliaItemKind.valueOf(kind) }.getOrDefault(MarginaliaItemKind.TEXT),
    xFraction = xFraction,
    yFraction = yFraction,
    widthFraction = widthFraction,
    heightFraction = heightFraction,
    zIndex = zIndex,
    text = text,
    assetId = assetId,
    linkedDocumentId = linkedDocumentId,
    linkedPageIndex = linkedPageIndex,
)

fun MarginaliaItem.toEntity(
    createdAtEpochMillis: Long = System.currentTimeMillis(),
): MarginaliaItemEntity = MarginaliaItemEntity(
    id = id,
    documentId = anchor.documentId,
    pageIndex = anchor.pageIndex,
    startOffset = anchor.startOffset,
    endOffsetExclusive = anchor.endOffsetExclusive,
    kind = kind.name,
    xFraction = xFraction,
    yFraction = yFraction,
    widthFraction = widthFraction,
    heightFraction = heightFraction,
    zIndex = zIndex,
    text = text,
    assetId = assetId,
    linkedDocumentId = linkedDocumentId,
    linkedPageIndex = linkedPageIndex,
    createdAtEpochMillis = createdAtEpochMillis,
)
