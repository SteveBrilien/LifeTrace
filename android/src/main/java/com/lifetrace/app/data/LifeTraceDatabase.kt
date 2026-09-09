package com.lifetrace.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey val id: String,
    val body: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val placeLabel: String,
)

@Entity(
    tableName = "diary_photos",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class DiaryPhotoEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val originalPath: String,
    val thumbnailPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sortOrder: Int,
)

data class DiaryWithPhotos(
    @Embedded val entry: DiaryEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val photos: List<DiaryPhotoEntity>,
)

@Dao
interface DiaryDao {
    @Transaction
    @Query("SELECT * FROM diary_entries ORDER BY occurredAt DESC, updatedAt DESC")
    fun observeAll(): Flow<List<DiaryWithPhotos>>

    @Transaction
    @Query("SELECT * FROM diary_entries ORDER BY occurredAt DESC, updatedAt DESC")
    suspend fun getAll(): List<DiaryWithPhotos>

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getById(id: String): DiaryWithPhotos?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DiaryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<DiaryPhotoEntity>)

    @Query("DELETE FROM diary_photos WHERE entryId = :entryId")
    suspend fun deletePhotos(entryId: String)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Transaction
    suspend fun replaceEntry(entry: DiaryEntryEntity, photos: List<DiaryPhotoEntity>) {
        upsertEntry(entry)
        deletePhotos(entry.id)
        if (photos.isNotEmpty()) insertPhotos(photos)
    }
}

@Database(
    entities = [DiaryEntryEntity::class, DiaryPhotoEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LifeTraceDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var instance: LifeTraceDatabase? = null

        fun getInstance(context: Context): LifeTraceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeTraceDatabase::class.java,
                    "lifetrace.db",
                ).build().also { instance = it }
            }
    }
}
