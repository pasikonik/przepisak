package pl.przepisak.data.remote.gemini

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.prefs.SecretStore
import pl.przepisak.data.prefs.SettingsRepository
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.ImportStrategy
import pl.przepisak.domain.model.ImportedIngredientGroup
import pl.przepisak.domain.model.ImportedRecipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klient Gemini API działający na kluczu użytkownika (RF-07).
 *
 * Rozmawiamy z REST-owym `generateContent` bez dodatkowego SDK. Dwa powody:
 * możemy wymusić odpowiedź w trybie JSON razem ze schematem (model rzadziej
 * wtedy improwizuje), a klucz odczytujemy przy każdym żądaniu — więc zmiana
 * w Ustawieniach działa od razu, bez restartu aplikacji.
 */
@Singleton
class GeminiApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : GeminiApi {

    override suspend fun extractRecipe(
        content: String,
        kind: GeminiContentKind,
        sourceUrl: String?,
        sourceName: String?,
    ): Outcome<ImportedRecipe> = withContext(io) {
        if (content.isBlank()) {
            return@withContext Outcome.Failure(AppError.ParsingFailed("brak treści do analizy"))
        }

        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(GeminiPrompts.build(kind, content.take(MAX_INPUT_CHARS)))),
                ),
            ),
            generationConfig = GeminiGenerationConfig(
                // Niska temperatura: chcemy wiernego przepisania przepisu,
                // nie twórczości modelu.
                temperature = 0.15,
                topP = 0.9,
                maxOutputTokens = 8192,
                responseMimeType = "application/json",
                responseSchema = GeminiSchema.recipeResponseSchema,
            ),
        )

        when (val response = call(request)) {
            is Outcome.Failure -> response
            is Outcome.Success -> parseRecipe(response.value, kind, sourceUrl, sourceName)
        }
    }

    override suspend fun testConnection(): Outcome<String> = withContext(io) {
        val model = settings.current().geminiModel
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart("Odpowiedz jednym słowem: OK")),
                ),
            ),
            // Modele z serii 2.5 domyślnie "myślą" przed odpowiedzią, a tokeny myślenia
            // liczą się do maxOutputTokens — przy zbyt małym limicie model potrafi
            // zużyć go cały na samo rozumowanie i nie zostawić nic na widoczną treść
            // (finishReason "MAX_TOKENS", brak parts). 128 daje zapas na to i odpowiedź.
            generationConfig = GeminiGenerationConfig(temperature = 0.0, maxOutputTokens = 128),
        )
        when (val response = call(request)) {
            is Outcome.Failure -> response
            is Outcome.Success -> Outcome.Success(model.displayName)
        }
    }

    /** Wysyła żądanie i zwraca surowy tekst wygenerowany przez model. */
    private suspend fun call(request: GeminiGenerateContentRequest): Outcome<String> {
        val apiKey = secrets.geminiApiKey?.takeIf { it.isNotBlank() }
            ?: return Outcome.Failure(AppError.MissingGeminiKey)
        val model = settings.current().geminiModel.apiName

        return try {
            val response = httpClient.post("$BASE_URL/$model:generateContent") {
                contentType(ContentType.Application.Json)
                // Klucz w nagłówku, nie w adresie — adresy trafiają do logów
                // i historii, a klucz nie powinien.
                header("x-goog-api-key", apiKey)
                setBody(request)
            }

            val body = response.bodyAsText()
            val status = response.status.value
            // Bezpieczne do zalogowania: to odpowiedź Gemini, klucz nigdy w niej nie wraca.
            android.util.Log.d(TAG, "generateContent status=$status body=$body")
            if (status !in 200..299) {
                return when (status) {
                    429 -> Outcome.Failure(AppError.GeminiQuotaExceeded)
                    else -> Outcome.Failure(AppError.Http(status))
                }
            }

            val parsed = json.decodeFromString<GeminiGenerateContentResponse>(body)
            parsed.error?.let { error ->
                return Outcome.Failure(AppError.GeminiInvalidResponse(error.message))
            }
            parsed.promptFeedback?.blockReason?.let { reason ->
                return Outcome.Failure(AppError.GeminiInvalidResponse("zablokowano: $reason"))
            }

            val text = parsed.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.trim()

            if (text.isNullOrBlank()) {
                Outcome.Failure(AppError.GeminiInvalidResponse("pusta odpowiedź modelu"))
            } else {
                Outcome.Success(text)
            }
        } catch (e: java.io.IOException) {
            // Brak sieci, zerwane połączenie, nieznany host — dla użytkownika to
            // wszystko jedna sytuacja.
            Outcome.Failure(AppError.NoNetwork(e))
        } catch (e: kotlinx.serialization.SerializationException) {
            Outcome.Failure(AppError.GeminiInvalidResponse(e.message))
        } catch (e: Exception) {
            Outcome.Failure(AppError.Unknown(e))
        }
    }

    private fun parseRecipe(
        rawText: String,
        kind: GeminiContentKind,
        sourceUrl: String?,
        sourceName: String?,
    ): Outcome<ImportedRecipe> {
        val dto = try {
            json.decodeFromString<GeminiRecipeDto>(stripCodeFence(rawText))
        } catch (e: kotlinx.serialization.SerializationException) {
            return Outcome.Failure(AppError.GeminiInvalidResponse(e.message))
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure(AppError.GeminiInvalidResponse(e.message))
        }

        val title = dto.title?.trim().orEmpty()
        if (title.isBlank()) {
            return Outcome.Failure(AppError.GeminiInvalidResponse("model nie podał nazwy przepisu"))
        }

        val groups = dto.ingredientGroups
            .map { group ->
                ImportedIngredientGroup(
                    name = group.name?.trim()?.takeIf { it.isNotBlank() },
                    lines = group.items.map { it.trim() }.filter { it.isNotBlank() },
                )
            }
            .filter { it.lines.isNotEmpty() }

        return Outcome.Success(
            ImportedRecipe(
                title = title,
                description = dto.description?.trim()?.takeIf { it.isNotBlank() },
                imageUrl = dto.imageUrl?.trim()?.takeIf { it.isNotBlank() },
                servings = dto.servings?.takeIf { it > 0 },
                servingsLabel = dto.servingsLabel?.trim()?.takeIf { it.isNotBlank() },
                prepTimeMinutes = dto.prepTimeMinutes?.takeIf { it > 0 },
                cookTimeMinutes = dto.cookTimeMinutes?.takeIf { it > 0 },
                totalTimeMinutes = dto.totalTimeMinutes?.takeIf { it > 0 },
                difficulty = dto.difficulty.toDifficulty(),
                categoryName = dto.category?.trim()?.takeIf { it.isNotBlank() },
                tagNames = dto.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                ingredientGroups = groups,
                steps = dto.steps.map { it.trim() }.filter { it.isNotBlank() },
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                notes = dto.notes?.trim()?.takeIf { it.isNotBlank() },
                strategy = kind.toStrategy(),
            ),
        )
    }

    /**
     * Model dostaje `responseMimeType = application/json`, ale i tak zdarza mu się
     * owinąć odpowiedź w blok markdownu. Obcinamy go, zamiast uznawać odpowiedź
     * za błędną.
     */
    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun String?.toDifficulty(): Difficulty? = when (this?.trim()?.uppercase()) {
        "EASY" -> Difficulty.EASY
        "MEDIUM" -> Difficulty.MEDIUM
        "HARD" -> Difficulty.HARD
        else -> null
    }

    private fun GeminiContentKind.toStrategy(): ImportStrategy = when (this) {
        GeminiContentKind.WEB_PAGE -> ImportStrategy.GEMINI_HTML
        GeminiContentKind.VIDEO_TRANSCRIPT -> ImportStrategy.GEMINI_VIDEO_TRANSCRIPT
        GeminiContentKind.VIDEO_DESCRIPTION -> ImportStrategy.GEMINI_VIDEO_DESCRIPTION
        GeminiContentKind.PLAIN_TEXT -> ImportStrategy.GEMINI_TEXT
    }

    private companion object {
        const val TAG = "GeminiApi"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Limit wejścia. Darmowy plan ma ograniczenie tokenów, a treść strony
         * z przepisem rzadko wymaga więcej — reszta to zwykle komentarze i stopka.
         */
        const val MAX_INPUT_CHARS = 60_000
    }
}
