package pl.przepisak.data.remote.gemini

import pl.przepisak.core.util.Outcome
import pl.przepisak.domain.model.ImportedRecipe

/** Rodzaj treści przekazywanej modelowi — decyduje o doborze promptu. */
enum class GeminiContentKind {
    /** Tekst wyłuskany ze strony WWW bez mikrodanych (RF-05). */
    WEB_PAGE,

    /** Transkrypcja (napisy) filmu (RF-06). */
    VIDEO_TRANSCRIPT,

    /** Sam opis filmu — napisów nie udało się pobrać (RF-06). */
    VIDEO_DESCRIPTION,

    /** Tekst wklejony przez użytkownika. */
    PLAIN_TEXT,
}

/**
 * Klient Google Gemini API działający na kluczu użytkownika (RF-07).
 *
 * Rozmawiamy z REST-owym `generateContent` wprost, bez dodatkowego SDK: pozwala
 * to wymusić odpowiedź w trybie JSON wraz ze schematem, a klucz podmienić w
 * locie po zmianie w Ustawieniach.
 */
interface GeminiApi {

    /**
     * Wyciąga przepis z podanej treści. [sourceUrl] i [sourceName] trafiają do
     * wyniku, żeby podgląd importu mógł pokazać źródło.
     *
     * Zwraca [pl.przepisak.core.util.AppError.MissingGeminiKey], gdy klucz nie
     * został skonfigurowany — UI kieruje wtedy użytkownika do Ustawień.
     */
    suspend fun extractRecipe(
        content: String,
        kind: GeminiContentKind,
        sourceUrl: String? = null,
        sourceName: String? = null,
    ): Outcome<ImportedRecipe>

    /** Sprawdza klucz i dostępność modelu. Zwraca nazwę użytego modelu. */
    suspend fun testConnection(): Outcome<String>
}
