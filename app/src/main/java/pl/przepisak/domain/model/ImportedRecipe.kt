package pl.przepisak.domain.model

/** Skąd wzięliśmy dane przepisu — pokazujemy to użytkownikowi przy podglądzie importu. */
enum class ImportStrategy {
    /** Dane strukturalne Schema.org/Recipe z JSON-LD (RF-05). */
    SCHEMA_ORG_JSONLD,

    /** Mikrodane Schema.org w atrybutach HTML (RF-05). */
    SCHEMA_ORG_MICRODATA,

    /** Treść strony przepuszczona przez Gemini (RF-05, brak mikrodanych). */
    GEMINI_HTML,

    /** Opis i napisy z filmu przepuszczone przez Gemini (RF-06). */
    GEMINI_VIDEO_TRANSCRIPT,

    /** Sam opis filmu — napisów nie udało się pobrać (RF-06). */
    GEMINI_VIDEO_DESCRIPTION,

    /** Tekst wklejony przez użytkownika, np. przepis skopiowany z czatu. */
    GEMINI_TEXT,

    /** Plik eksportu z aplikacji Cookmate (.mcb/.xml). */
    COOKMATE_FILE,
}

/** Grupa składników w wyniku importu — jeszcze bez identyfikatorów bazy. */
data class ImportedIngredientGroup(
    val name: String? = null,
    val lines: List<String> = emptyList(),
)

/**
 * Przepis odczytany ze źródła zewnętrznego, przed zapisem do bazy.
 *
 * Składniki są tu jeszcze surowymi liniami tekstu — parsowanie na
 * ilość/jednostkę/nazwę robi `QuantityParser` przy mapowaniu na [RecipeAggregate].
 */
data class ImportedRecipe(
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val servings: Int? = null,
    val servingsLabel: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val totalTimeMinutes: Int? = null,
    val difficulty: Difficulty? = null,
    val categoryName: String? = null,
    val tagNames: List<String> = emptyList(),
    val ingredientGroups: List<ImportedIngredientGroup> = emptyList(),
    val steps: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val notes: String? = null,
    val strategy: ImportStrategy,
    /** Komunikaty w rodzaju „nie znaleziono zdjęcia” — pokazujemy je w podglądzie. */
    val warnings: List<String> = emptyList(),
) {
    val ingredientCount: Int get() = ingredientGroups.sumOf { it.lines.size }
    val isUsable: Boolean get() = title.isNotBlank() && (ingredientCount > 0 || steps.isNotEmpty())
}

/** Rozpoznany typ udostępnionego odnośnika (RF-04). */
sealed interface ImportSource {
    val url: String

    /** Zwykła strona z przepisem. */
    data class WebPage(override val url: String) : ImportSource

    /** Film na YouTube — mamy dostęp do opisu i zwykle do napisów. */
    data class YouTubeVideo(override val url: String, val videoId: String) : ImportSource

    /** TikTok, Instagram, Facebook — dostajemy tylko opis przez oEmbed. */
    data class ShortVideo(override val url: String, val platform: String) : ImportSource
}
