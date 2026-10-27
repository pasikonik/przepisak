package pl.przepisak.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.przepisak.domain.model.TimerState

/**
 * Minutnik kuchenny (RF-13).
 *
 * Zapis w bazie, a nie w pamięci, jest tu celowy: minutnik ma dożyć końca także
 * wtedy, gdy Android ubije proces aplikacji. Dla stanu `RUNNING` prawdą jest
 * [endAtEpochMs] — odliczanie w UI jest tylko jego prezentacją.
 */
@Entity(
    tableName = "timers",
    indices = [Index(value = ["state"]), Index(value = ["recipe_id"])],
)
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Int,
    @ColumnInfo(name = "remaining_seconds") val remainingSeconds: Int,
    @ColumnInfo(name = "end_at") val endAtEpochMs: Long? = null,
    val state: TimerState,
    @ColumnInfo(name = "recipe_id") val recipeId: Long? = null,
    @ColumnInfo(name = "step_position") val stepPosition: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
