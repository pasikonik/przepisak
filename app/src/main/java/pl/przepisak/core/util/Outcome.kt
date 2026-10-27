package pl.przepisak.core.util

/**
 * Wynik operacji, która może się nie udać w sposób przewidywalny (sieć, AI, import).
 *
 * Nie używamy `kotlin.Result`, bo chcemy nieść własny, przetłumaczalny opis błędu
 * i sterować komunikatem w UI bez oglądania klas wyjątków.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> =
    also { if (it is Outcome.Success) action(it.value) }

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> =
    also { if (it is Outcome.Failure) action(it.error) }

fun <T> T.asSuccess(): Outcome<T> = Outcome.Success(this)

fun AppError.asFailure(): Outcome<Nothing> = Outcome.Failure(this)

/**
 * Błędy, które aplikacja potrafi opisać użytkownikowi.
 *
 * [messageArg] niesie szczegół techniczny (np. kod HTTP) doklejany do komunikatu.
 */
sealed class AppError(val messageArg: String? = null, val cause: Throwable? = null) {

    /** Brak połączenia albo host nieosiągalny. */
    class NoNetwork(cause: Throwable? = null) : AppError(cause = cause)

    /** Serwer odpowiedział błędem. */
    class Http(val code: Int, cause: Throwable? = null) : AppError(code.toString(), cause)

    /** Nie udało się odczytać strony jako przepisu. */
    class ParsingFailed(detail: String? = null, cause: Throwable? = null) : AppError(detail, cause)

    /** Nie skonfigurowano klucza Gemini API (RF-07). */
    data object MissingGeminiKey : AppError()

    /** Gemini odpowiedziało, ale nie dało się odczytać JSON-a. */
    class GeminiInvalidResponse(detail: String? = null) : AppError(detail)

    /** Wyczerpany limit darmowego planu Gemini. */
    data object GeminiQuotaExceeded : AppError()

    /** Brak konfiguracji Supabase (URL / klucz anon). */
    data object SupabaseNotConfigured : AppError()

    /** Użytkownik nie jest zalogowany do Supabase. */
    data object NotAuthenticated : AppError()

    /** Tabela `recipes` lub kubełek na zdjęcia nie istnieją — nie uruchomiono supabase/schema.sql. */
    data object SupabaseSchemaMissing : AppError()

    /** Nieobsługiwany adres udostępniony do importu. */
    class UnsupportedUrl(url: String? = null) : AppError(url)

    /** Film nie ma napisów ani użytecznego opisu (RF-06). */
    data object NoTranscriptAvailable : AppError()

    /** Nie udało się przetworzyć zdjęcia. */
    class ImageProcessingFailed(detail: String? = null, cause: Throwable? = null) : AppError(detail, cause)

    /** Błąd zapisu / odczytu bazy lub plików. */
    class Storage(detail: String? = null, cause: Throwable? = null) : AppError(detail, cause)

    /** Cokolwiek, czego nie przewidzieliśmy. */
    class Unknown(cause: Throwable? = null) : AppError(cause?.message, cause)
}
