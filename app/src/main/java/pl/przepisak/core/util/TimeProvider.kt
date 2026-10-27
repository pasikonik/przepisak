package pl.przepisak.core.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Źródło czasu. Wstrzykujemy je zamiast wołać `System.currentTimeMillis()`
 * bezpośrednio, żeby testy minutników i synchronizacji mogły sterować zegarem.
 */
interface TimeProvider {
    /** Czas ścienny (UTC, ms) — używany do znaczników zapisu i minutników. */
    fun nowMs(): Long

    /** Monotoniczny czas od startu systemu (ms) — nie skacze przy zmianie strefy. */
    fun elapsedRealtimeMs(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMs(): Long = android.os.SystemClock.elapsedRealtime()
}
