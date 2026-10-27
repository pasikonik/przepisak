package pl.przepisak.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.przepisak.domain.model.Difficulty

/**
 * Kolumny zdjęcia. Osadzamy je (zamiast osobnej tabeli), bo zdjęcie należy
 * do dokładnie jednego właściciela — przepisu albo kroku.
 *
 * Wszystkie pola są opcjonalne, dzięki czemu `@Embedded` może być niepuste
 * i Room nie musi zgadywać, czy cała struktura jest `null`.
 */
data class PhotoColumns(
    @ColumnInfo(name = "local_file") val localFileName: String? = null,
    @ColumnInfo(name = "remote_path") val remotePath: String? = null,
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
)

/** Kolumny formy do pieczenia, dla której podano proporcje (RF-09). */
data class PanColumns(
    @ColumnInfo(name = "shape") val shape: String? = null,
    @ColumnInfo(name = "diameter_cm") val diameterCm: Double? = null,
    @ColumnInfo(name = "length_cm") val lengthCm: Double? = null,
    @ColumnInfo(name = "width_cm") val widthCm: Double? = null,
    @ColumnInfo(name = "inner_diameter_cm") val innerDiameterCm: Double? = null,
    @ColumnInfo(name = "height_cm") val heightCm: Double? = null,
    @ColumnInfo(name = "label") val label: String? = null,
)

/**
 * Nagłówek przepisu.
 *
 * Pola synchronizacji:
 *  - [remoteId] — UUID nadawany lokalnie przy tworzeniu, klucz wiersza w Supabase,
 *  - [isDirty] — są lokalne zmiany do wypchnięcia (RF-15),
 *  - [deletedAt] — „nagrobek”; wiersz zostaje, żeby usunięcie dotarło na inne urządzenia,
 *  - [lastSyncedAt] — znacznik z ostatniej udanej wysyłki/pobrania.
 */
@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["remote_id"], unique = true),
        Index(value = ["category_id"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_favorite"]),
        Index(value = ["is_dirty"]),
        Index(value = ["deleted_at"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: String,
    val title: String,
    /** Tytuł bez diakrytyków — używany do sortowania alfabetycznego. */
    @ColumnInfo(name = "title_normalized") val titleNormalized: String,
    val description: String? = null,
    @ColumnInfo(name = "prep_time_minutes") val prepTimeMinutes: Int? = null,
    @ColumnInfo(name = "cook_time_minutes") val cookTimeMinutes: Int? = null,
    /** Ręcznie podany czas całkowity; gdy `null`, sumujemy przygotowanie i pieczenie. */
    @ColumnInfo(name = "total_time_minutes") val totalTimeMinutes: Int? = null,
    val difficulty: Difficulty? = null,
    val servings: Int? = null,
    @ColumnInfo(name = "servings_label") val servingsLabel: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @Embedded(prefix = "photo_") val photo: PhotoColumns = PhotoColumns(),
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    @ColumnInfo(name = "source_name") val sourceName: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val rating: Int? = null,
    val notes: String? = null,
    @Embedded(prefix = "pan_") val pan: PanColumns = PanColumns(),
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_dirty") val isDirty: Boolean = true,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
)
