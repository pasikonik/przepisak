package pl.przepisak.data.remote.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Modele DTO REST-owego wywołania `generateContent` Gemini oraz kształt przepisu,
 * o który prosimy model (`GeminiRecipeDto`).
 *
 * Nazwy pól odpowiadają dosłownie kluczom JSON-a z publicznego API Google — dzięki
 * temu `kotlinx.serialization` nie potrzebuje żadnych `@SerialName`.
 */

/** Pojedynczy fragment treści. API wspiera też obraz/audio, ale tu wysyłamy sam tekst. */
@Serializable
data class GeminiPart(
    val text: String,
)

/** Jedna „tura" rozmowy z modelem. Przy `generateContent` wysyłamy zawsze jedną, rolę „user". */
@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

/** Próg blokowania treści dla jednej kategorii bezpieczeństwa. */
@Serializable
data class GeminiSafetySetting(
    val category: String,
    val threshold: String,
)

/**
 * Domyślne progi bezpieczeństwa dla importu przepisów.
 *
 * Standardowe (domyślne u Google) progi bloków potrafią uciąć przepis, który
 * wspomina alkohol w marynacie albo ostrą papryczkę — to fałszywe alarmy, nie
 * treści, przed którymi trzeba chronić użytkownika. `BLOCK_ONLY_HIGH` blokuje
 * tylko treści jednoznacznie szkodliwe, zostawiając kulinaria w spokoju.
 */
internal val GEMINI_DEFAULT_SAFETY_SETTINGS: List<GeminiSafetySetting> = listOf(
    GeminiSafetySetting(category = "HARM_CATEGORY_HARASSMENT", threshold = "BLOCK_ONLY_HIGH"),
    GeminiSafetySetting(category = "HARM_CATEGORY_HATE_SPEECH", threshold = "BLOCK_ONLY_HIGH"),
    GeminiSafetySetting(category = "HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold = "BLOCK_ONLY_HIGH"),
    GeminiSafetySetting(category = "HARM_CATEGORY_DANGEROUS_CONTENT", threshold = "BLOCK_ONLY_HIGH"),
)

/**
 * Parametry generowania.
 *
 * [responseMimeType] razem z [responseSchema] wymuszają na modelu czysty JSON
 * zgodny ze strukturą [GeminiRecipeDto] — bez tego model chętnie owija odpowiedź
 * w markdown albo dopisuje komentarz.
 */
@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
)

/** Ciało żądania `POST /v1beta/models/{model}:generateContent`. */
@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val safetySettings: List<GeminiSafetySetting> = GEMINI_DEFAULT_SAFETY_SETTINGS,
)

/** Pojedynczy fragment tekstu w odpowiedzi modelu. */
@Serializable
data class GeminiResponsePart(
    val text: String? = null,
)

/** Treść wygenerowana przez model w ramach jednego kandydata. */
@Serializable
data class GeminiResponseContent(
    val parts: List<GeminiResponsePart> = emptyList(),
    val role: String? = null,
)

/** Jedna propozycja odpowiedzi. Gemini teoretycznie potrafi zwrócić kilka — bierzemy pierwszą. */
@Serializable
data class GeminiCandidate(
    val content: GeminiResponseContent? = null,
    /** np. "STOP", "MAX_TOKENS", "SAFETY" — przy "SAFETY" treść bywa pusta. */
    val finishReason: String? = null,
)

/** Informacja o ewentualnym zablokowaniu całego zapytania (jeszcze przed wygenerowaniem kandydatów). */
@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null,
)

/** Szczegóły błędu zwracane przez API w odpowiedziach ze statusem spoza 2xx. */
@Serializable
data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

/**
 * Odpowiedź `generateContent`.
 *
 * Jeden model na obie ścieżki (sukces i błąd): przy statusie spoza 2xx ciało
 * odpowiedzi ma inny kształt (`error`), więc reszta pól po prostu zostaje pusta —
 * dzięki `explicitNulls = false` i `coerceInputValues = true` w [kotlinx.serialization.json.Json]
 * (patrz `NetworkModule`) nie trzeba tego rozróżniać dwoma osobnymi typami.
 */
@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
    val error: GeminiErrorDetail? = null,
)

/** Grupa składników w odpowiedzi modelu — jeszcze surowe linie tekstu. */
@Serializable
data class GeminiIngredientGroupDto(
    val name: String? = null,
    val items: List<String> = emptyList(),
)

/**
 * Przepis, o który prosimy Gemini — kształt wymuszony przez [GeminiSchema.recipeResponseSchema].
 *
 * Wszystkie pola mają wartości domyślne: model potrafi coś pominąć (zwłaszcza przy
 * [pl.przepisak.data.remote.gemini.GeminiContentKind.VIDEO_DESCRIPTION], gdzie
 * prompt wprost zabrania mu zgadywania), a brakujący klucz w JSON-ie nie powinien
 * wywalać deserializacji.
 */
@Serializable
data class GeminiRecipeDto(
    val title: String? = null,
    val description: String? = null,
    val servings: Int? = null,
    val servingsLabel: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val totalTimeMinutes: Int? = null,
    /** "EASY" / "MEDIUM" / "HARD" — mapowane na [pl.przepisak.domain.model.Difficulty]. */
    val difficulty: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val ingredientGroups: List<GeminiIngredientGroupDto> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: String? = null,
    val imageUrl: String? = null,
)
