package pl.przepisak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.przepisak.data.local.entity.TimerEntity

/** Minutniki kuchenne (RF-13). */
@Dao
interface TimerDao {

    @Query("SELECT * FROM timers ORDER BY created_at ASC")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers WHERE state IN ('RUNNING', 'PAUSED') ORDER BY created_at ASC")
    fun observeActive(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers WHERE state IN ('RUNNING', 'PAUSED') ORDER BY created_at ASC")
    suspend fun getActive(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE state = 'RUNNING' ORDER BY end_at ASC")
    suspend fun getRunning(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getById(id: Long): TimerEntity?

    @Query("SELECT * FROM timers WHERE recipe_id = :recipeId AND step_position = :stepPosition AND state IN ('RUNNING', 'PAUSED') LIMIT 1")
    suspend fun findForStep(recipeId: Long, stepPosition: Int): TimerEntity?

    @Insert
    suspend fun insert(timer: TimerEntity): Long

    @Update
    suspend fun update(timer: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM timers WHERE state = 'FINISHED'")
    suspend fun deleteFinished()

    @Query("DELETE FROM timers")
    suspend fun deleteAll()
}
