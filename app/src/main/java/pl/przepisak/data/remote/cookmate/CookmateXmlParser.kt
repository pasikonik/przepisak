package pl.przepisak.data.remote.cookmate

import org.w3c.dom.Element
import pl.przepisak.domain.model.ImportStrategy
import pl.przepisak.domain.model.ImportedIngredientGroup
import pl.przepisak.domain.model.ImportedRecipe
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Przepis z eksportu Cookmate wraz z polami, których nie ma w [ImportedRecipe]
 * (ocena, nazwa lokalnego pliku zdjęcia — rozwiązywana osobno względem
 * archiwum .mcb, bo sama treść XML nie zawiera bajtów obrazu).
 */
data class CookmateImportedRecipe(
    val imported: ImportedRecipe,
    val rating: Int?,
    val localImageFileName: String?,
)

/**
 * Parsuje eksport z aplikacji Cookmate (fr.cookbook / „My CookBook") — format
 * `<cookbook><recipe>...</recipe></cookbook>`, ten sam w samodzielnym pliku .xml
 * i w pliku XML zaszytym wewnątrz archiwum .mcb.
 */
class CookmateXmlParser @Inject constructor() {

    fun parse(xml: String): List<CookmateImportedRecipe> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            // Xerces-owa flaga hartująca przeciw XXE — parser wbudowany w Androida
            // (inna implementacja niż na JVM) potrafi nie rozpoznać tego URI i rzucić
            // ParserConfigurationException; brak DOCTYPE i tak nie zaszkodzi.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val recipeNodes = document.getElementsByTagName("recipe")
        return (0 until recipeNodes.length).map { index -> parseRecipe(recipeNodes.item(index) as Element) }
    }

    private fun parseRecipe(recipe: Element): CookmateImportedRecipe {
        val (servings, servingsLabel) = parseServings(recipe.childText("quantity"))
        val localImageFileName = recipe.child("image")?.child("path")?.textOrNull()?.substringAfterLast('/')

        val imported = ImportedRecipe(
            title = recipe.childText("title").orEmpty(),
            description = recipe.liTexts("description")
                .filter { it.isNotBlank() }
                .ifEmpty { null }
                ?.joinToString("\n"),
            imageUrl = recipe.childText("imageurl"),
            servings = servings,
            servingsLabel = servingsLabel,
            prepTimeMinutes = parseMinutes(recipe.childText("preptime")),
            cookTimeMinutes = parseMinutes(recipe.childText("cooktime")),
            totalTimeMinutes = parseMinutes(recipe.childText("totaltime")),
            categoryName = recipe.childText("category"),
            ingredientGroups = listOf(ImportedIngredientGroup(lines = recipe.liTexts("ingredient"))),
            steps = recipe.liTexts("recipetext").filter { it.isNotBlank() },
            sourceUrl = recipe.childText("url"),
            sourceName = recipe.liTexts("source")
                .filter { it.isNotBlank() }
                .ifEmpty { null }
                ?.joinToString(", "),
            notes = buildNotes(recipe),
            strategy = ImportStrategy.COOKMATE_FILE,
        )

        return CookmateImportedRecipe(
            imported = imported,
            rating = recipe.childText("rating")?.toIntOrNull()?.takeIf { it > 0 },
            localImageFileName = localImageFileName,
        )
    }

    /** Cookmate nie ma pól na wartości odżywcze — dopisujemy je do notatek, żeby nic nie zginęło. */
    private fun buildNotes(recipe: Element): String? {
        val comments = recipe.liTexts("comments").filter { it.isNotBlank() }
        val nutrition = recipe.liTexts("nutrition").filter { it.isNotBlank() }
        val parts = buildList {
            addAll(comments)
            if (nutrition.isNotEmpty()) {
                add("Wartości odżywcze:")
                addAll(nutrition)
            }
        }
        return parts.ifEmpty { null }?.joinToString("\n")
    }

    /** Formaty spotkane w realnych eksportach: „05m", „1h00m", „45min", a nawet „24M". */
    private fun parseMinutes(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = TIME_PATTERN.find(raw) ?: return null
        val minutesGroup = match.groupValues[2]
        if (minutesGroup.isEmpty() && match.groupValues[1].isEmpty()) return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = minutesGroup.toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    /**
     * „4” → (4, null); „2 porcje” → (2, "porcje"); „2-3” → (2, null) — bierzemy dolną
     * granicę zakresu, resztę zapisu porzucamy zamiast zgadywać, co znaczy „-3”.
     */
    private fun parseServings(raw: String?): Pair<Int?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val match = LEADING_INT.find(raw) ?: return null to null
        val servings = match.value.toInt()
        val rest = raw.substring(match.range.last + 1).trim()
        val label = rest.takeIf { it.isNotEmpty() && it.none(Char::isDigit) }
        return servings to label
    }

    private companion object {
        val TIME_PATTERN = Regex("(?:(\\d+)\\s*h)?\\s*(\\d+)?\\s*m", RegexOption.IGNORE_CASE)
        val LEADING_INT = Regex("^(\\d+)")
    }
}

private fun Element.child(tag: String): Element? {
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element && node.tagName == tag) return node
    }
    return null
}

private fun Element.textOrNull(): String? = textContent?.trim()?.ifEmpty { null }

private fun Element.childText(tag: String): String? = child(tag)?.textOrNull()

private fun Element.liTexts(tag: String): List<String> {
    val container = child(tag) ?: return emptyList()
    val nodes = container.getElementsByTagName("li")
    return (0 until nodes.length).flatMap { cleanHtml((nodes.item(it) as Element).textContent.orEmpty()) }
}

/**
 * Cookmate czasem zeskrobał przepis ze strony razem z formatowaniem — po
 * odkodowaniu encji XML zostaje z tego dosłowny HTML w tekście (np. `<b>Spód</b>`,
 * a czasem cały zlepiony blok `<h2>...</h2>Spód:<ul><li>...</li></ul>` w JEDNYM
 * `<li>`). Zamieniamy tagi blokowe na nowe linie i rozbijamy wynik na osobne
 * pozycje, zamiast pokazywać użytkownikowi surowe znaczniki.
 */
private fun cleanHtml(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val withBreaks = raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(li|p|h[1-6]|div)>"), "\n")
        .replace(Regex("(?i)<(li|p|h[1-6]|div)(?:\\s[^>]*)?>"), "\n")
    val stripped = withBreaks.replace(Regex("<[^>]*>"), "")
    return stripped.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}
