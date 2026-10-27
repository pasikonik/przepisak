package pl.przepisak.domain.model

/** Poziom trudności przepisu. */
enum class Difficulty { EASY, MEDIUM, HARD }

/**
 * Odwołanie do zdjęcia. Aplikacja jest offline-first, więc źródłem prawdy jest
 * plik lokalny; pozostałe pola pozwalają odtworzyć zdjęcie po synchronizacji
 * lub dociągnąć je z importu.
 */
data class PhotoRef(
    /** Nazwa pliku w katalogu `filesDir/photos`. */
    val localFileName: String? = null,
    /** Ścieżka obiektu w Supabase Storage, np. `<userId>/<recipeUuid>/foto.jpg`. */
    val remotePath: String? = null,
    /** Oryginalny adres z importu — używany, dopóki zdjęcie nie zostanie pobrane. */
    val sourceUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = localFileName == null && remotePath == null && sourceUrl.isNullOrBlank()

    companion object {
        val EMPTY = PhotoRef()
        fun fromUrl(url: String?) = if (url.isNullOrBlank()) EMPTY else PhotoRef(sourceUrl = url)
    }
}

/** Kategoria główna przepisu (jedna na przepis). */
data class Category(
    val id: Long = 0,
    val name: String,
    val iconKey: String? = null,
    val position: Int = 0,
)

/** Tag (przepis może mieć wiele). */
data class Tag(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int? = null,
)

/** Pojedynczy składnik. */
data class Ingredient(
    val id: Long = 0,
    val position: Int = 0,
    val name: String,
    val quantity: Quantity? = null,
    val unit: MeasureUnit? = null,
    /** Jednostka nierozpoznana, zapisana dosłownie (np. „opak. 500 g”). */
    val unitLabel: String? = null,
    val note: String? = null,
    val isOptional: Boolean = false,
    /** `false` dla „sól do smaku” — takich pozycji nie skalujemy. */
    val isScalable: Boolean = true,
    /** Oryginalna linia z przepisu — zachowujemy ją, by nic nie zgubić. */
    val rawText: String = "",
) {
    val hasQuantity: Boolean get() = quantity != null
}

/**
 * Grupa składników, np. „Ciasto” / „Krem” / „Do dekoracji”.
 * Grupa domyślna ma [name] == `null` i zawsze istnieje jako pierwsza.
 */
data class IngredientGroup(
    val id: Long = 0,
    val name: String? = null,
    val position: Int = 0,
    val ingredients: List<Ingredient> = emptyList(),
) {
    val isDefault: Boolean get() = name.isNullOrBlank()
}

/** Krok przygotowania. */
data class Step(
    val id: Long = 0,
    val position: Int = 0,
    val text: String,
    val photo: PhotoRef = PhotoRef.EMPTY,
    /** Minutnik przypięty ręcznie przez użytkownika (sekundy). */
    val timerSeconds: Int? = null,
)

/** Nagłówek przepisu — bez składników i kroków. */
data class Recipe(
    val id: Long = 0,
    /** UUID stały dla całego życia przepisu; klucz w Supabase. */
    val remoteId: String,
    val title: String,
    val description: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    /** Jeśli `null`, liczymy jako suma [prepTimeMinutes] i [cookTimeMinutes]. */
    val totalTimeMinutesOverride: Int? = null,
    val difficulty: Difficulty? = null,
    val servings: Int? = null,
    /** Nazwa porcji: „porcje”, „sztuki”, „tortownica 24 cm”. */
    val servingsLabel: String? = null,
    val categoryId: Long? = null,
    val photo: PhotoRef = PhotoRef.EMPTY,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val isFavorite: Boolean = false,
    /** Ocena 1..5, `null` gdy nieoceniony. */
    val rating: Int? = null,
    val notes: String? = null,
    /** Forma, dla której podano proporcje (RF-09). */
    val pan: BakingPan? = null,
    val createdAtEpochMs: Long = 0,
    val updatedAtEpochMs: Long = 0,
) {
    val totalTimeMinutes: Int?
        get() = totalTimeMinutesOverride
            ?: listOfNotNull(prepTimeMinutes, cookTimeMinutes).takeIf { it.isNotEmpty() }?.sum()
}

/** Pełny przepis ze wszystkimi zależnościami — model dla ekranu szczegółów. */
data class RecipeAggregate(
    val recipe: Recipe,
    val groups: List<IngredientGroup> = emptyList(),
    val steps: List<Step> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val category: Category? = null,
) {
    val allIngredients: List<Ingredient> get() = groups.flatMap { it.ingredients }

    companion object {
        fun empty(remoteId: String, nowMs: Long) = RecipeAggregate(
            recipe = Recipe(
                remoteId = remoteId,
                title = "",
                createdAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
            ),
            groups = listOf(IngredientGroup()),
        )
    }
}

/** Lekka projekcja dla listy przepisów (RF-03). */
data class RecipeListItem(
    val id: Long,
    val title: String,
    val description: String?,
    val photo: PhotoRef,
    val totalTimeMinutes: Int?,
    val difficulty: Difficulty?,
    val servings: Int?,
    val isFavorite: Boolean,
    val rating: Int?,
    val categoryName: String?,
    val tagNames: List<String>,
    val updatedAtEpochMs: Long,
)
