package com.example.smartgallery.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val dateAdded: Long,
    val locationName: String? = null,
    val category: String = "Разное",
    val isFavorite: Boolean = false,
    val isVault: Boolean = false
)

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverFaceUri: String,
    val isConfirmed: Boolean = false
)

@Entity(
    tableName = "faces",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["photoId"]), Index(value = ["personId"])]
)
data class FaceEntity(
    @PrimaryKey val id: String,
    val photoId: String,
    val personId: String,
    val boundingBoxJson: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Dao
interface GalleryDao {
    @Query("SELECT * FROM photos WHERE isVault = 0 ORDER BY dateAdded DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM persons")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT p.* FROM photos p INNER JOIN faces f ON p.id = f.photoId WHERE f.personId = :personId")
    fun getPhotosForPerson(personId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM faces")
    suspend fun getAllFaces(): List<FaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(face: FaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Query("UPDATE persons SET name = :newName, isConfirmed = 1 WHERE id = :personId")
    suspend fun renamePerson(personId: String, newName: String)

    @Query("UPDATE faces SET personId = :targetPersonId WHERE personId = :sourcePersonId")
    suspend fun mergePersons(sourcePersonId: String, targetPersonId: String)
}

@Database(entities = [PhotoEntity::class, PersonEntity::class, FaceEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun galleryDao(): GalleryDao

    companion object {
        @Volatile
        private var instance: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "smart_gallery.db"
                ).build().also { instance = it }
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromFloatArray(array: FloatArray): String = array.joinToString(",")

    @TypeConverter
    fun toFloatArray(data: String): FloatArray =
        if (data.isEmpty()) FloatArray(0) else data.split(",").map { it.toFloat() }.toFloatArray()
}