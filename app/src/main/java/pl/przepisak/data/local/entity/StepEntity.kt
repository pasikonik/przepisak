package pl.przepisak.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Krok przygotowania, opcjonalnie ze zdjęciem etapu (RF-02). */
@Entity(
    tableName = "steps",
    indices = [Index(value = ["recipe_id"])],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    val position: Int = 0,
    val text: String,
    @Embedded(prefix = "photo_") val photo: PhotoColumns = PhotoColumns(),
    /** Minutnik przypięty ręcznie; wykrywane automatycznie liczymy w locie (RF-13). */
    @ColumnInfo(name = "timer_seconds") val timerSeconds: Int? = null,
)
