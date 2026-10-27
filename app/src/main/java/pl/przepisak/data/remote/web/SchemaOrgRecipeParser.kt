package pl.przepisak.data.remote.web

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.przepisak.domain.model.ImportStrategy
import pl.przepisak.domain.model.ImportedIngredientGroup
import pl.przepisak.domain.model.ImportedRecipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Odczytuje przepis z danych strukturalnych Schema.org zapisanych jako JSON-LD (RF-05).
 *
 * To najważniejsza ścieżka importu: nic nie kosztuje, nie wymaga klucza API i daje
 * dane dokładnie takie, jakie opublikował autor przepisu. Gemini jest dopiero
 * ostatnią deską ratunku.
 *
 * Rzeczywiste strony bywają niechlujne, dlatego parser jest tolerancyjny: `@type`
 * może być napisem albo tablicą, przepis potrafi być schowany w `@graph` albo
 * w `mainEntity`, a pola tekstowe pojawiają się raz jako napis, raz jako obiekt
 * i raz jako tablica.
 */
@Singleton
class SchemaOrgRecipeParser @Inject constructor(
    private val json: Json,
    private val textExtractor: HtmlTextExtractor,
) {

    fun parse(page: FetchedPage): ImportedRecipe? {
        val document = runCatching { Ksoup.parse(html = page.html) }.getOrNull() ?: return null
        val scripts = runCatching {
            document.select("script[type=application/ld+json]").map { it.html() }
        }.getOrDefault(emptyList())

        val recipe = scripts
            .asSequence()
            // Jeden zepsuty skrypt na stronie nie może przerwać całego importu.
            .mapNotNull { raw -> runCatching { json.parseToJsonElement(raw) }.getOrNull() }
            .mapNotNull { element -> findRecipe(element) }
            .firstOrNull()
            ?: return null

        val ingredients = recipe.stringList("recipeIngredient")
            .ifEmpty { recipe.stringList("ingredients") }
            .map { clean(it) }
            .filter { it.isNotBlank() }

        val steps = extractSteps(recipe["recipeInstructions"])

        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val title = clean(recipe.string("name").orEmpty())
        if (title.isBlank()) return null

        val warnings = mutableListOf<String>()
        val imageUrl = extractImage(recipe) ?: textExtractor.extractImage(page.html, page.finalUrl)
        if (imageUrl == null) warnings += "Nie znalazłem zdjęcia potrawy."
        if (steps.isEmpty()) warnings += "Strona nie podała kroków przygotowania."
        if (ingredients.isEmpty()) warnings += "Strona nie podała listy składników."

        return ImportedRecipe(
            title = title,
            description = recipe.string("description")?.let { clean(it) }?.takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
            servings = extractServings(recipe["recipeYield"]),
            prepTimeMinutes = IsoDurationParser.toMinutes(recipe.string("prepTime")),
            cookTimeMinutes = IsoDurationParser.toMinutes(recipe.string("cookTime")),
            totalTimeMinutes = IsoDurationParser.toMinutes(recipe.string("totalTime")),
            categoryName = recipe.firstOf("recipeCategory")?.let { clean(it) },
            tagNames = extractTags(recipe),
            ingredientGroups = listOf(ImportedIngredientGroup(lines = ingredients)),
            steps = steps,
            sourceUrl = page.finalUrl,
            sourceName = extractAuthor(recipe) ?: textExtractor.extractSiteName(page.html, page.finalUrl),
            strategy = ImportStrategy.SCHEMA_ORG_JSONLD,
            warnings = warnings,
        )
    }

    // -----------------------------------------------------------------------
    // Wyszukiwanie obiektu przepisu
    // -----------------------------------------------------------------------

    /** Przechodzi drzewo JSON-LD w poszukiwaniu obiektu o typie zawierającym „Recipe”. */
    private fun findRecipe(element: JsonElement, depth: Int = 0): JsonObject? {
        if (depth > MAX_DEPTH) return null
        return when (element) {
            is JsonArray -> element.firstNotNullOfOrNull { findRecipe(it, depth + 1) }
            is JsonObject -> {
                if (element.hasRecipeType()) return element
                listOf("@graph", "mainEntity", "mainEntityOfPage", "itemListElement")
                    .asSequence()
                    .mapNotNull { key -> element[key] }
                    .mapNotNull { findRecipe(it, depth + 1) }
                    .firstOrNull()
            }
            else -> null
        }
    }

    private fun JsonObject.hasRecipeType(): Boolean {
        val type = this["@type"] ?: return false
        return when (type) {
            is JsonArray -> type.any { it.asStringOrNull()?.contains("Recipe", ignoreCase = true) == true }
            else -> type.asStringOrNull()?.contains("Recipe", ignoreCase = true) == true
        }
    }

    // -----------------------------------------------------------------------
    // Pola przepisu
    // -----------------------------------------------------------------------

    /**
     * Instrukcje przychodzą w czterech postaciach: jako jeden napis, tablica napisów,
     * tablica `HowToStep` albo tablica `HowToSection` z zagnieżdżonymi krokami.
     */
    private fun extractSteps(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()

        is JsonArray -> element.flatMap { item ->
            when (item) {
                is JsonObject -> when {
                    item["itemListElement"] != null -> {
                        val sectionName = item.string("name")?.let { clean(it) }
                        val nested = extractSteps(item["itemListElement"])
                        // Nazwę sekcji zostawiamy jako krok-nagłówek, żeby nie zgubić
                        // podziału „ciasto / krem / dekoracja”.
                        if (!sectionName.isNullOrBlank() && nested.isNotEmpty()) {
                            listOf("— ${sectionName.uppercase()} —") + nested
                        } else {
                            nested
                        }
                    }
                    else -> listOfNotNull(item.string("text") ?: item.string("name"))
                }
                else -> listOfNotNull(item.asStringOrNull())
            }
        }.map { clean(it) }.filter { it.isNotBlank() }

        else -> element.asStringOrNull()?.let { splitPlainInstructions(clean(it)) }.orEmpty()
    }

    /** Pojedynczy blok tekstu dzielimy na kroki po nowych liniach albo po numeracji. */
    private fun splitPlainInstructions(text: String): List<String> {
        val byLines = text.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (byLines.size > 1) return byLines.map { it.removeLeadingNumber() }

        val byNumbers = Regex("(?<=\\.)\\s*(?=\\d+[.)]\\s)").split(text)
        if (byNumbers.size > 1) {
            return byNumbers.map { it.trim().removeLeadingNumber() }.filter { it.isNotBlank() }
        }
        return listOf(text)
    }

    private fun String.removeLeadingNumber(): String =
        replace(Regex("^\\s*\\d+[.)]\\s*"), "").trim()

    private fun extractServings(element: JsonElement?): Int? {
        val raw = when (element) {
            null -> null
            is JsonArray -> element.firstNotNullOfOrNull { it.asStringOrNull() }
            else -> element.asStringOrNull()
        } ?: return null
        return Regex("\\d+").find(raw)?.value?.toIntOrNull()?.takeIf { it in 1..999 }
    }

    private fun extractImage(recipe: JsonObject): String? = when (val image = recipe["image"]) {
        null -> null
        is JsonArray -> image.firstNotNullOfOrNull { imageUrlOf(it) }
        else -> imageUrlOf(image)
    }

    private fun imageUrlOf(element: JsonElement): String? = when (element) {
        is JsonObject -> element.string("url") ?: element.string("contentUrl")
        else -> element.asStringOrNull()
    }?.trim()?.takeIf { it.startsWith("http") }

    private fun extractAuthor(recipe: JsonObject): String? = when (val author = recipe["author"]) {
        null -> null
        is JsonArray -> author.firstNotNullOfOrNull { authorNameOf(it) }
        else -> authorNameOf(author)
    }

    private fun authorNameOf(element: JsonElement): String? = when (element) {
        is JsonObject -> element.string("name")
        else -> element.asStringOrNull()
    }?.let { clean(it) }?.takeIf { it.isNotBlank() }

    private fun extractTags(recipe: JsonObject): List<String> {
        val keywords = when (val value = recipe["keywords"]) {
            null -> emptyList()
            is JsonArray -> value.mapNotNull { it.asStringOrNull() }
            else -> value.asStringOrNull()?.split(',').orEmpty()
        }
        val cuisine = recipe.stringList("recipeCuisine")
        return (keywords + cuisine)
            .map { clean(it) }
            .filter { it.isNotBlank() && it.length <= MAX_TAG_LENGTH }
            .distinct()
            .take(MAX_TAGS)
    }

    // -----------------------------------------------------------------------
    // Pomocnicze
    // -----------------------------------------------------------------------

    private fun JsonObject.string(key: String): String? = this[key]?.asStringOrNull()

    private fun JsonObject.firstOf(key: String): String? = when (val value = this[key]) {
        null -> null
        is JsonArray -> value.firstNotNullOfOrNull { it.asStringOrNull() }
        else -> value.asStringOrNull()
    }

    private fun JsonObject.stringList(key: String): List<String> = when (val value = this[key]) {
        null -> emptyList()
        is JsonArray -> value.mapNotNull { it.asStringOrNull() }
        else -> listOfNotNull(value.asStringOrNull())
    }

    private fun JsonElement.asStringOrNull(): String? = when (this) {
        // JsonNull dziedziczy po JsonPrimitive, a jego `content` to napis "null" —
        // bez tego sprawdzenia wstawialibyśmy do przepisu słowo „null”.
        JsonNull -> null
        is JsonPrimitive -> content.takeIf { it.isNotBlank() }
        is JsonObject -> runCatching { jsonObject["name"]?.jsonPrimitive?.content }.getOrNull()
        is JsonArray -> runCatching { jsonArray.firstOrNull()?.jsonPrimitive?.content }.getOrNull()
    }

    private fun clean(text: String): String =
        textExtractor.decodeEntities(text)
            // Twarda spacja z HTML-a nie jest białym znakiem dla wyrażenia \\s.
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    private companion object {
        const val MAX_DEPTH = 6
        const val MAX_TAGS = 8
        const val MAX_TAG_LENGTH = 30
    }
}
