package pl.przepisak.domain.scaling

import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.RecipeAggregate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Przelicza proporcje przepisu przy zmianie liczby porcji (RF-08).
 */
class RecipeScaler @Inject constructor(
    private val unitConverter: UnitConverter,
) {

    /** Mnożnik dla zmiany liczby porcji; 1.0, gdy brakuje danych. */
    fun factorFor(originalServings: Int?, targetServings: Int?): Double {
        if (originalServings == null || targetServings == null) return 1.0
        if (originalServings <= 0 || targetServings <= 0) return 1.0
        return targetServings.toDouble() / originalServings.toDouble()
    }

    /**
     * Skaluje jedną pozycję. Składników oznaczonych jako nieskalowalne („sól do
     * smaku”) nie ruszamy — pomnożenie ich przez 2 dałoby zapis bez sensu.
     */
    fun scaleIngredient(ingredient: Ingredient, factor: Double, prettify: Boolean = true): Ingredient {
        val quantity = ingredient.quantity
        if (!ingredient.isScalable || quantity == null || factor == 1.0) return ingredient

        val scaled = quantity.scaled(factor)
        if (!prettify) return ingredient.copy(quantity = scaled)

        val (adjusted, unit) = unitConverter.prettify(scaled, ingredient.unit)
        return ingredient.copy(quantity = adjusted, unit = unit)
    }

    fun scaleGroup(group: IngredientGroup, factor: Double, prettify: Boolean = true): IngredientGroup =
        group.copy(ingredients = group.ingredients.map { scaleIngredient(it, factor, prettify) })

    /**
     * Skaluje cały przepis.
     *
     * Czasów przygotowania i pieczenia **nie** skalujemy, a treści kroków nie
     * ruszamy. Podwójna porcja ciasta nie piecze się dwa razy dłużej — czas zależy
     * od grubości i temperatury, nie od masy. Zmyślony czas byłby gorszy niż
     * zostawienie oryginalnego.
     */
    fun scaleAggregate(aggregate: RecipeAggregate, factor: Double): RecipeAggregate {
        if (factor == 1.0) return aggregate
        return aggregate.copy(
            recipe = aggregate.recipe.copy(
                servings = aggregate.recipe.servings
                    ?.let { (it * factor).roundToInt().coerceAtLeast(1) },
            ),
            groups = aggregate.groups.map { scaleGroup(it, factor) },
        )
    }

    /** Skalowanie do wskazanej liczby porcji — wygodny skrót dla warstwy UI. */
    fun scaleToServings(aggregate: RecipeAggregate, targetServings: Int?): RecipeAggregate =
        scaleAggregate(aggregate, factorFor(aggregate.recipe.servings, targetServings))
}
