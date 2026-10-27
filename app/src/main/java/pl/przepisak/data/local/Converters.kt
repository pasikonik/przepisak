package pl.przepisak.data.local

import androidx.room.TypeConverter
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.TimerState

/**
 * Konwertery typów dla Rooma.
 *
 * Wyliczenia zapisujemy jako nazwy, nie jako liczby porządkowe — dodanie nowej
 * wartości w środku enuma nie może przemianować danych już zapisanych na urządzeniu.
 * Nieznana nazwa (np. po cofnięciu wersji aplikacji) daje `null` zamiast wyjątku.
 */
class Converters {

    @TypeConverter
    fun difficultyToString(value: Difficulty?): String? = value?.name

    @TypeConverter
    fun stringToDifficulty(value: String?): Difficulty? =
        value?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun measureUnitToString(value: MeasureUnit?): String? = value?.name

    @TypeConverter
    fun stringToMeasureUnit(value: String?): MeasureUnit? =
        value?.let { name -> MeasureUnit.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun timerStateToString(value: TimerState): String = value.name

    @TypeConverter
    fun stringToTimerState(value: String): TimerState =
        TimerState.entries.firstOrNull { it.name == value } ?: TimerState.PAUSED
}
