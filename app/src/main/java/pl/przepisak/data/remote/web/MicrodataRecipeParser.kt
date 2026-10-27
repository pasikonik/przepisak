package pl.przepisak.data.remote.web

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import pl.przepisak.domain.model.ImportStrategy
import pl.przepisak.domain.model.ImportedIngredientGroup
import pl.przepisak.domain.model.ImportedRecipe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ścieżka zapasowa dla stron, które opisują przepis mikrodanymi w atrybutach HTML,
 * a nie JSON-LD (RF-05). Spotyka się to na starszych blogach kulinarnych.
 */
@Singleton
class MicrodataRecipeParser @Inject constructor(
    private val textExtractor: HtmlTextExtractor,
) {

    fun parse(page: FetchedPage): ImportedRecipe? {
        val document = runCatching { Ksoup.parse(html = page.html) }.getOrNull() ?: return null
        val scope = runCatching { document.selectFirst("[itemtype*=Recipe]") }.getOrNull() ?: return null

        val ingredients = scope.propValues("recipeIngredient")
            .ifEmpty { scope.propValues("ingredients") }
        val steps = scope.propValues("recipeInstructions")
            .flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val title = scope.propValue("name")
            ?: textExtractor.extractTitle(page.html)
            ?: return null

        return ImportedRecipe(
            title = title,
            description = scope.propValue("description"),
            imageUrl = scope.propAttr("image", "src")
                ?: scope.propAttr("image", "content")
                ?: textExtractor.extractImage(page.html, page.finalUrl),
            servings = scope.propValue("recipeYield")
                ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
                ?.takeIf { it in 1..999 },
            prepTimeMinutes = IsoDurationParser.toMinutes(scope.timeValue("prepTime")),
            cookTimeMinutes = IsoDurationParser.toMinutes(scope.timeValue("cookTime")),
            totalTimeMinutes = IsoDurationParser.toMinutes(scope.timeValue("totalTime")),
            categoryName = scope.propValue("recipeCategory"),
            ingredientGroups = listOf(ImportedIngredientGroup(lines = ingredients)),
            steps = steps,
            sourceUrl = page.finalUrl,
            sourceName = scope.propValue("author")
                ?: textExtractor.extractSiteName(page.html, page.finalUrl),
            strategy = ImportStrategy.SCHEMA_ORG_MICRODATA,
            warnings = buildList {
                if (steps.isEmpty()) add("Strona nie podała kroków przygotowania.")
                if (ingredients.isEmpty()) add("Strona nie podała listy składników.")
            },
        )
    }

    private fun Element.propValues(prop: String): List<String> =
        runCatching { select("[itemprop=$prop]").map { it.text().normalize() } }
            .getOrDefault(emptyList())
            .filter { it.isNotBlank() }

    private fun Element.propValue(prop: String): String? =
        runCatching { selectFirst("[itemprop=$prop]")?.text()?.normalize() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun Element.propAttr(prop: String, attribute: String): String? =
        runCatching { selectFirst("[itemprop=$prop]")?.attr(attribute) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.startsWith("http") }

    /**
     * Czasy w mikrodanych siedzą zwykle w atrybucie `datetime` albo `content`
     * elementu `<time>`, a nie w jego treści — treść bywa opisowa („około godziny”).
     */
    private fun Element.timeValue(prop: String): String? {
        val element = runCatching { selectFirst("[itemprop=$prop]") }.getOrNull() ?: return null
        return element.attr("datetime").takeIf { it.isNotBlank() }
            ?: element.attr("content").takeIf { it.isNotBlank() }
            ?: element.text().normalize().takeIf { it.isNotBlank() }
    }

    private fun String.normalize(): String =
        replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()
}
