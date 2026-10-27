package pl.przepisak.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.Quantity
import pl.przepisak.domain.model.Recipe
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.Step

class RecipeScalerTest {

    private val scaler = RecipeScaler(UnitConverter())

    private fun ingredient(
        name: String,
        amount: Double?,
        unit: MeasureUnit?,
        scalable: Boolean = true,
    ) = Ingredient(
        name = name,
        quantity = amount?.let { Quantity(it) },
        unit = unit,
        isScalable = scalable,
        rawText = name,
    )

    // --- współczynnik ------------------------------------------------------

    @Test
    fun `liczy wspolczynnik ze zmiany liczby porcji`() {
        assertEquals(2.0, scaler.factorFor(4, 8), DELTA)
        assertEquals(0.5, scaler.factorFor(4, 2), DELTA)
        assertEquals(1.5, scaler.factorFor(2, 3), DELTA)
    }

    @Test
    fun `bez danych o porcjach wspolczynnik wynosi jeden`() {
        assertEquals(1.0, scaler.factorFor(null, 8), DELTA)
        assertEquals(1.0, scaler.factorFor(4, null), DELTA)
        assertEquals(1.0, scaler.factorFor(0, 8), DELTA)
        assertEquals(1.0, scaler.factorFor(4, 0), DELTA)
    }

    // --- pojedynczy składnik ----------------------------------------------

    @Test
    fun `skaluje ilosc skladnika`() {
        val scaled = scaler.scaleIngredient(ingredient("mąka", 250.0, MeasureUnit.GRAM), 2.0)
        assertEquals(500.0, scaled.quantity!!.amount, DELTA)
        assertEquals(MeasureUnit.GRAM, scaled.unit)
    }

    @Test
    fun `po przeskalowaniu dobiera wygodniejsza jednostke`() {
        val scaled = scaler.scaleIngredient(ingredient("mąka", 500.0, MeasureUnit.GRAM), 3.0)
        assertEquals(MeasureUnit.KILOGRAM, scaled.unit)
        assertEquals(1.5, scaled.quantity!!.amount, DELTA)
    }

    @Test
    fun `zachowuje jednostke gdy wylaczono upiekszanie`() {
        val scaled = scaler.scaleIngredient(
            ingredient("mąka", 500.0, MeasureUnit.GRAM),
            factor = 3.0,
            prettify = false,
        )
        assertEquals(MeasureUnit.GRAM, scaled.unit)
        assertEquals(1500.0, scaled.quantity!!.amount, DELTA)
    }

    @Test
    fun `nie skaluje skladnika oznaczonego jako nieskalowalny`() {
        val source = ingredient("sól do smaku", 1.0, MeasureUnit.PINCH, scalable = false)
        val scaled = scaler.scaleIngredient(source, 4.0)
        assertEquals(1.0, scaled.quantity!!.amount, DELTA)
    }

    @Test
    fun `nie rusza skladnika bez podanej ilosci`() {
        val source = ingredient("pieprz", null, null)
        assertNull(scaler.scaleIngredient(source, 3.0).quantity)
    }

    @Test
    fun `wspolczynnik rowny jednosci nie zmienia niczego`() {
        val source = ingredient("mąka", 250.0, MeasureUnit.GRAM)
        assertEquals(source, scaler.scaleIngredient(source, 1.0))
    }

    @Test
    fun `skaluje oba konce zakresu`() {
        val source = Ingredient(
            name = "czosnek",
            quantity = Quantity(1.0, 2.0),
            unit = MeasureUnit.CLOVE,
            rawText = "1-2 ząbki czosnku",
        )
        val scaled = scaler.scaleIngredient(source, 2.0)
        assertEquals(2.0, scaled.quantity!!.amount, DELTA)
        assertEquals(4.0, scaled.quantity!!.amountMax!!, DELTA)
    }

    // --- cały przepis -----------------------------------------------------

    private fun aggregate(servings: Int?) = RecipeAggregate(
        recipe = Recipe(
            remoteId = "test",
            title = "Ciasto",
            servings = servings,
            prepTimeMinutes = 20,
            cookTimeMinutes = 45,
        ),
        groups = listOf(
            IngredientGroup(
                name = "Ciasto",
                ingredients = listOf(
                    ingredient("mąka", 250.0, MeasureUnit.GRAM),
                    ingredient("sól", null, null, scalable = false),
                ),
            ),
            IngredientGroup(
                name = "Krem",
                ingredients = listOf(ingredient("śmietanka", 200.0, MeasureUnit.MILLILITER)),
            ),
        ),
        steps = listOf(Step(text = "Piecz 45 minut w 180 stopniach.")),
    )

    @Test
    fun `skaluje wszystkie grupy skladnikow`() {
        val scaled = scaler.scaleAggregate(aggregate(4), 2.0)
        assertEquals(500.0, scaled.groups[0].ingredients[0].quantity!!.amount, DELTA)
        assertEquals(400.0, scaled.groups[1].ingredients[0].quantity!!.amount, DELTA)
    }

    @Test
    fun `aktualizuje liczbe porcji`() {
        assertEquals(8, scaler.scaleAggregate(aggregate(4), 2.0).recipe.servings)
        assertEquals(2, scaler.scaleAggregate(aggregate(4), 0.5).recipe.servings)
    }

    @Test
    fun `liczba porcji nigdy nie schodzi ponizej jednej`() {
        assertEquals(1, scaler.scaleAggregate(aggregate(2), 0.1).recipe.servings)
    }

    @Test
    fun `nie skaluje czasow ani tresci krokow`() {
        // Podwójna porcja ciasta nie piecze się dwa razy dłużej.
        val scaled = scaler.scaleAggregate(aggregate(4), 2.0)
        assertEquals(20, scaled.recipe.prepTimeMinutes)
        assertEquals(45, scaled.recipe.cookTimeMinutes)
        assertEquals("Piecz 45 minut w 180 stopniach.", scaled.steps.single().text)
    }

    @Test
    fun `skalowanie do liczby porcji korzysta z liczby z przepisu`() {
        val scaled = scaler.scaleToServings(aggregate(4), 6)
        assertEquals(375.0, scaled.groups[0].ingredients[0].quantity!!.amount, DELTA)
        assertEquals(6, scaled.recipe.servings)
    }

    @Test
    fun `przepis bez liczby porcji nie jest zmieniany przy skalowaniu do porcji`() {
        val scaled = scaler.scaleToServings(aggregate(null), 6)
        assertEquals(250.0, scaled.groups[0].ingredients[0].quantity!!.amount, DELTA)
        assertNull(scaled.recipe.servings)
    }

    @Test
    fun `skladniki nieskalowalne zostaja nietkniete takze w calym przepisie`() {
        val scaled = scaler.scaleAggregate(aggregate(4), 3.0)
        val salt = scaled.groups[0].ingredients[1]
        assertNull(salt.quantity)
        assertTrue(salt.name == "sól")
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
