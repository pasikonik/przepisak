package pl.przepisak.data.local.seed

import pl.przepisak.core.util.TextNormalizer
import pl.przepisak.data.local.entity.CategoryEntity
import pl.przepisak.data.local.entity.TagEntity

/**
 * Dane startowe (kategorie i tagi) wstawiane do bazy przy pierwszym uruchomieniu
 * przez `PrzepisakDatabase.seedCallback`.
 */
object TaxonomySeed {

    fun categories(): List<CategoryEntity> = listOf(
        category("Ciasta i torty", "cake", 0),
        category("Ciastka i deser", "cookie", 1),
        category("Obiady", "dinner", 2),
        category("Zupy", "soup", 3),
        category("Śniadania", "breakfast", 4),
        category("Kolacje", "supper", 5),
        category("Przekąski", "snack", 6),
        category("Sałatki", "salad", 7),
        category("Pieczywo", "bread", 8),
        category("Napoje", "drink", 9),
        category("Sosy i dipy", "sauce", 10),
        category("Przetwory", "jar", 11),
        category("Dla dzieci", "child", 12),
        category("Inne", "other", 13),
    )

    fun tags(): List<TagEntity> = listOf(
        tag("Wegańskie"),
        tag("Wegetariańskie"),
        tag("Bezglutenowe"),
        tag("Bez laktozy"),
        tag("Szybkie"),
        tag("Na słodko"),
        tag("Na ostro"),
        tag("Święta"),
        tag("Wielkanoc"),
        tag("Boże Narodzenie"),
        tag("Grill"),
        tag("Jednogarnkowe"),
        tag("Fit"),
        tag("Tanie"),
        tag("Dla gości"),
        tag("Bez pieczenia"),
        tag("Z piekarnika"),
        tag("Na zimno"),
        tag("Domowe"),
        tag("Tradycyjne"),
        tag("Kuchnia włoska"),
        tag("Kuchnia azjatycka"),
        tag("Kuchnia polska"),
        tag("Fasola i strączki"),
    )

    private fun category(name: String, iconKey: String, position: Int): CategoryEntity =
        CategoryEntity(
            name = name,
            nameNormalized = TextNormalizer.normalize(name),
            iconKey = iconKey,
            position = position,
        )

    private fun tag(name: String): TagEntity =
        TagEntity(
            name = name,
            nameNormalized = TextNormalizer.normalize(name),
            colorArgb = null,
        )
}
