package pl.przepisak.domain.model

/** Stan minutnika kuchennego (RF-13). */
enum class TimerState { RUNNING, PAUSED, FINISHED }

/**
 * Minutnik. Zapisujemy go w bazie, bo ma działać także wtedy, gdy system ubije
 * proces aplikacji: dla stanu [TimerState.RUNNING] liczy się [endAtEpochMs],
 * a nie odliczanie w pamięci.
 */
data class CookingTimer(
    val id: Long = 0,
    val label: String,
    val totalSeconds: Int,
    /** Ile zostało w momencie pauzy. Ignorowane, gdy minutnik działa. */
    val remainingSecondsWhenPaused: Int = totalSeconds,
    /** Moment zakończenia. Ustawiony tylko dla [TimerState.RUNNING]. */
    val endAtEpochMs: Long? = null,
    val state: TimerState = TimerState.PAUSED,
    val recipeId: Long? = null,
    val stepPosition: Int? = null,
    val createdAtEpochMs: Long = 0,
) {
    /** Ile sekund zostało przy podanym „teraz”. Nigdy nie schodzi poniżej zera. */
    fun remainingSecondsAt(nowMs: Long): Int = when (state) {
        TimerState.RUNNING -> endAtEpochMs
            ?.let { ((it - nowMs) / 1000.0).toInt().coerceAtLeast(0) }
            ?: remainingSecondsWhenPaused
        TimerState.PAUSED -> remainingSecondsWhenPaused
        TimerState.FINISHED -> 0
    }

    fun progressAt(nowMs: Long): Float =
        if (totalSeconds <= 0) 1f
        else 1f - (remainingSecondsAt(nowMs).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    fun hasElapsed(nowMs: Long): Boolean =
        state == TimerState.RUNNING && endAtEpochMs != null && endAtEpochMs <= nowMs
}

/** Minutnik wykryty w treści kroku (RF-13). */
data class DetectedTimer(
    /** Dosłowny fragment tekstu, np. „25 minut”. */
    val matchedText: String,
    val seconds: Int,
    /** Zakres w tekście kroku — pozwala podkreślić fragment w UI. */
    val startIndex: Int,
    val endIndex: Int,
) {
    /** Dla „20-25 minut” bierzemy górną granicę; tu trzymamy ją jawnie. */
    val label: String get() = matchedText.trim()
}
