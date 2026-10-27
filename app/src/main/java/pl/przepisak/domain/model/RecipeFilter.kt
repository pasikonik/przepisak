package pl.przepisak.domain.model

/** Kolejność sortowania listy przepisów. */
enum class RecipeSort(val labelKey: String) {
    RECENT("recent"),
    TITLE_ASC("title"),
    TIME_ASC("time"),
    RATING_DESC("rating"),
}

/**
 * Kryteria filtrowania listy przepisów (RF-03).
 *
 * Wszystkie pola są opcjonalne i łączą się operatorem AND. [tagIds] wymaga
 * posiadania **wszystkich** wskazanych tagów — dzięki temu filtr zawęża wyniki
 * zgodnie z intuicją.
 */
data class RecipeFilter(
    val query: String = "",
    val categoryIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val difficulties: Set<Difficulty> = emptySet(),
    val maxTotalTimeMinutes: Int? = null,
    val onlyFavorites: Boolean = false,
    val minRating: Int? = null,
    val sort: RecipeSort = RecipeSort.RECENT,
) {
    val hasActiveFilters: Boolean
        get() = categoryIds.isNotEmpty() || tagIds.isNotEmpty() || difficulties.isNotEmpty() ||
            maxTotalTimeMinutes != null || onlyFavorites || minRating != null

    val isEmpty: Boolean get() = query.isBlank() && !hasActiveFilters
}
