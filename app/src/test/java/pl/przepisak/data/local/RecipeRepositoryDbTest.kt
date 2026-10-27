package pl.przepisak.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.przepisak.core.util.TimeProvider
import pl.przepisak.data.repository.RecipeRepositoryImpl
import pl.przepisak.domain.model.Category
import pl.przepisak.domain.model.Difficulty
import pl.przepisak.domain.model.Ingredient
import pl.przepisak.domain.model.IngredientGroup
import pl.przepisak.domain.model.MeasureUnit
import pl.przepisak.domain.model.Quantity
import pl.przepisak.domain.model.Recipe
import pl.przepisak.domain.model.RecipeAggregate
import pl.przepisak.domain.model.RecipeFilter
import pl.przepisak.domain.model.RecipeSort
import pl.przepisak.domain.model.Step
import pl.przepisak.domain.model.Tag
import java.util.UUID

/**
 * Testy na prawdziwej bazie (SQLite w pamięci, uruchamiane przez Robolectrica).
 *
 * Sprawdzają rzeczy, których nie da się zweryfikować czytając kod: czy zapytania
 * SQL są poprawne, czy indeks pełnotekstowy działa bez czułości na polskie znaki,
 * czy filtrowanie składa warunki tak, jak zakładamy, i czy dane startowe wchodzą
 * do bazy z właściwymi nazwami kolumn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecipeRepositoryDbTest {

    private lateinit var db: PrzepisakDatabase
    private lateinit var repository: RecipeRepositoryImpl

    private var now: Long = 1_700_000_000_000

    private val time = object : TimeProvider {
        override fun nowMs(): Long = now
        override fun elapsedRealtimeMs(): Long = now
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PrzepisakDatabase::class.java)
            .addCallback(PrzepisakDatabase.seedCallback)
            .allowMainThreadQueries()
            .build()
        repository = RecipeRepositoryImpl(
            db = db,
            recipeDao = db.recipeDao(),
            categoryDao = db.categoryDao(),
            tagDao = db.tagDao(),
            time = time,
            io = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- dane startowe ----------------------------------------------------

    @Test
    fun `dane startowe wchodza do bazy`() = runTest {
        assertTrue(db.categoryDao().getAll().isNotEmpty())
        assertTrue(db.tagDao().getAll().isNotEmpty())
        // RF-10: wbudowana baza wagowa musi być gotowa od pierwszego uruchomienia.
        assertTrue(db.densityDao().count() >= 100)
    }

    @Test
    fun `baza wagowa zawiera punkty odniesienia ze specyfikacji`() = runTest {
        // Nazwy w bazie są uszczegółowione („Mąka pszenna uniwersalna (typ 500)”),
        // więc szukamy tak, jak robi to aplikacja — po nazwie i aliasach.
        val flour = db.densityDao().search("maka pszenna").firstOrNull()
        assertNotNull("Brak mąki pszennej w bazie wagowej", flour)
        assertEquals(160.0, flour!!.gramsPerCup!!, 1.0)

        val sugar = db.densityDao().search("cukier bialy").firstOrNull()
        assertNotNull("Brak cukru w bazie wagowej", sugar)
        assertEquals(200.0, sugar!!.gramsPerCup!!, 1.0)
    }

    @Test
    fun `dopasowanie skladnika znajduje wpis bazy wagowej`() = runTest {
        // To ścieżka używana przy skalowaniu przepisu: nazwa przychodzi w formie
        // odmienionej, dokładnie tak jak stoi w przepisie.
        val repository = pl.przepisak.data.repository.DensityRepositoryImpl(
            densityDao = db.densityDao(),
            io = UnconfinedTestDispatcher(),
        )
        assertNotNull(repository.matchForIngredient("mąka pszenna"))
        assertNotNull(repository.matchForIngredient("cukier"))
    }

    @Test
    fun `baza wagowa nie ma powtorzonych nazw`() = runTest {
        val all = db.densityDao().observeAll().first()
        val duplicates = all.groupBy { it.nameNormalized }.filter { it.value.size > 1 }
        assertTrue("Powtórzone nazwy: ${duplicates.keys}", duplicates.isEmpty())
    }

    // --- zapis i odczyt ---------------------------------------------------

    private fun sampleRecipe(
        title: String = "Szarlotka babci",
        servings: Int? = 8,
        difficulty: Difficulty? = Difficulty.MEDIUM,
        favorite: Boolean = false,
        prep: Int? = 30,
        cook: Int? = 45,
        categoryName: String? = "Ciasta i torty",
        tagNames: List<String> = listOf("Na słodko", "Święta"),
    ) = RecipeAggregate(
        recipe = Recipe(
            remoteId = UUID.randomUUID().toString(),
            title = title,
            description = "Kruche ciasto z jabłkami",
            prepTimeMinutes = prep,
            cookTimeMinutes = cook,
            difficulty = difficulty,
            servings = servings,
            isFavorite = favorite,
        ),
        groups = listOf(
            IngredientGroup(
                name = "Ciasto",
                ingredients = listOf(
                    Ingredient(
                        name = "mąka pszenna",
                        quantity = Quantity(300.0),
                        unit = MeasureUnit.GRAM,
                        rawText = "300 g mąki pszennej",
                    ),
                    Ingredient(
                        name = "masło",
                        quantity = Quantity(200.0),
                        unit = MeasureUnit.GRAM,
                        rawText = "200 g masła",
                    ),
                ),
            ),
            IngredientGroup(
                name = "Nadzienie",
                ingredients = listOf(
                    Ingredient(
                        name = "jabłka",
                        quantity = Quantity(1.0),
                        unit = MeasureUnit.KILOGRAM,
                        rawText = "1 kg jabłek",
                    ),
                ),
            ),
        ),
        steps = listOf(
            Step(text = "Zagnieć ciasto i schłodź 30 minut."),
            Step(text = "Piecz 45 minut w 180 stopniach."),
        ),
        tags = tagNames.map { Tag(name = it) },
        category = categoryName?.let { Category(name = it) },
    )

    @Test
    fun `zapisuje i odczytuje caly przepis`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        val loaded = repository.getRecipe(id)

        assertNotNull(loaded)
        assertEquals("Szarlotka babci", loaded!!.recipe.title)
        assertEquals(2, loaded.groups.size)
        assertEquals("Ciasto", loaded.groups[0].name)
        assertEquals(2, loaded.groups[0].ingredients.size)
        assertEquals("Nadzienie", loaded.groups[1].name)
        assertEquals(2, loaded.steps.size)
        assertEquals(2, loaded.tags.size)
        assertEquals("Ciasta i torty", loaded.category?.name)
        assertEquals(75, loaded.recipe.totalTimeMinutes)
    }

    @Test
    fun `zachowuje kolejnosc grup skladnikow i krokow`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        val loaded = repository.getRecipe(id)!!
        assertEquals(listOf(0, 1), loaded.groups.map { it.position })
        assertEquals(listOf("mąka pszenna", "masło"), loaded.groups[0].ingredients.map { it.name })
        assertEquals(listOf(0, 1), loaded.steps.map { it.position })
        assertTrue(loaded.steps[0].text.startsWith("Zagnieć"))
    }

    @Test
    fun `zaklada kategorie i tagi podane sama nazwa`() = runTest {
        val before = db.categoryDao().getAll().size
        repository.saveRecipe(sampleRecipe(categoryName = "Zupełnie nowa kategoria"))
        assertEquals(before + 1, db.categoryDao().getAll().size)
        assertNotNull(db.tagDao().findByNormalizedName("na slodko"))
    }

    @Test
    fun `nie duplikuje kategorii przy dwoch przepisach`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Pierwszy"))
        val count = db.categoryDao().getAll().size
        repository.saveRecipe(sampleRecipe(title = "Drugi"))
        assertEquals(count, db.categoryDao().getAll().size)
    }

    @Test
    fun `ponowny zapis nie mnozy skladnikow`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        val loaded = repository.getRecipe(id)!!
        repository.saveRecipe(loaded.copy(recipe = loaded.recipe.copy(title = "Szarlotka poprawiona")))

        val again = repository.getRecipe(id)!!
        assertEquals("Szarlotka poprawiona", again.recipe.title)
        assertEquals(2, again.groups.size)
        assertEquals(2, again.groups[0].ingredients.size)
        assertEquals(2, again.steps.size)
    }

    // --- wyszukiwanie pełnotekstowe (RF-03) -------------------------------

    private suspend fun search(query: String) =
        repository.observeRecipes(RecipeFilter(query = query)).first()

    @Test
    fun `znajduje przepis po tytule`() = runTest {
        repository.saveRecipe(sampleRecipe())
        assertEquals(1, search("szarlotka").size)
    }

    @Test
    fun `znajduje przepis po skladniku`() = runTest {
        repository.saveRecipe(sampleRecipe())
        assertEquals(1, search("jabłka").size)
        assertEquals(1, search("masło").size)
    }

    @Test
    fun `wyszukiwanie ignoruje polskie znaki`() = runTest {
        repository.saveRecipe(sampleRecipe())
        // Wpisane bez diakrytyków ma znaleźć to samo co z nimi.
        assertEquals(1, search("jablka").size)
        assertEquals(1, search("maslo").size)
        assertEquals(1, search("SZARLOTKA").size)
    }

    @Test
    fun `wyszukiwanie dopasowuje przedrostek`() = runTest {
        repository.saveRecipe(sampleRecipe())
        assertEquals(1, search("szarl").size)
    }

    @Test
    fun `wyszukiwanie po tagu i kategorii`() = runTest {
        repository.saveRecipe(sampleRecipe())
        assertEquals(1, search("święta").size)
        assertEquals(1, search("ciasta").size)
    }

    @Test
    fun `brak dopasowania daje pusta liste`() = runTest {
        repository.saveRecipe(sampleRecipe())
        assertTrue(search("sushi").isEmpty())
    }

    @Test
    fun `indeks jest aktualizowany po edycji`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        val loaded = repository.getRecipe(id)!!
        repository.saveRecipe(loaded.copy(recipe = loaded.recipe.copy(title = "Sernik wiedeński")))

        assertTrue(search("szarlotka").isEmpty())
        assertEquals(1, search("sernik").size)
    }

    // --- filtrowanie ------------------------------------------------------

    @Test
    fun `pusty filtr zwraca wszystkie przepisy`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Pierwszy"))
        repository.saveRecipe(sampleRecipe(title = "Drugi"))
        assertEquals(2, repository.observeRecipes(RecipeFilter()).first().size)
    }

    @Test
    fun `filtruje ulubione`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Zwykły", favorite = false))
        repository.saveRecipe(sampleRecipe(title = "Ulubiony", favorite = true))

        val favorites = repository.observeRecipes(RecipeFilter(onlyFavorites = true)).first()
        assertEquals(1, favorites.size)
        assertEquals("Ulubiony", favorites.single().title)
    }

    @Test
    fun `filtruje po poziomie trudnosci`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Łatwy", difficulty = Difficulty.EASY))
        repository.saveRecipe(sampleRecipe(title = "Trudny", difficulty = Difficulty.HARD))

        val easy = repository.observeRecipes(
            RecipeFilter(difficulties = setOf(Difficulty.EASY)),
        ).first()
        assertEquals(1, easy.size)
        assertEquals("Łatwy", easy.single().title)
    }

    @Test
    fun `filtruje po maksymalnym czasie`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Szybki", prep = 5, cook = 10))
        repository.saveRecipe(sampleRecipe(title = "Długi", prep = 60, cook = 120))

        val quick = repository.observeRecipes(RecipeFilter(maxTotalTimeMinutes = 30)).first()
        assertEquals(1, quick.size)
        assertEquals("Szybki", quick.single().title)
    }

    @Test
    fun `filtruje po kategorii`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Ciasto", categoryName = "Ciasta i torty"))
        repository.saveRecipe(sampleRecipe(title = "Zupa", categoryName = "Zupy"))

        val soupId = db.categoryDao().findByNormalizedName("zupy")!!.id
        val soups = repository.observeRecipes(RecipeFilter(categoryIds = setOf(soupId))).first()
        assertEquals(1, soups.size)
        assertEquals("Zupa", soups.single().title)
    }

    @Test
    fun `filtr po tagach wymaga wszystkich wskazanych tagow`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Oba", tagNames = listOf("Szybkie", "Fit")))
        repository.saveRecipe(sampleRecipe(title = "Jeden", tagNames = listOf("Szybkie")))

        val quick = db.tagDao().findByNormalizedName("szybkie")!!.id
        val fit = db.tagDao().findByNormalizedName("fit")!!.id

        assertEquals(2, repository.observeRecipes(RecipeFilter(tagIds = setOf(quick))).first().size)
        val both = repository.observeRecipes(RecipeFilter(tagIds = setOf(quick, fit))).first()
        assertEquals(1, both.size)
        assertEquals("Oba", both.single().title)
    }

    @Test
    fun `lista zwraca nazwy tagow i kategorii`() = runTest {
        repository.saveRecipe(sampleRecipe())
        val item = repository.observeRecipes(RecipeFilter()).first().single()
        assertEquals("Ciasta i torty", item.categoryName)
        assertEquals(2, item.tagNames.size)
        assertEquals(75, item.totalTimeMinutes)
    }

    @Test
    fun `sortuje alfabetycznie bez czulosci na diakrytyki`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Żurek"))
        repository.saveRecipe(sampleRecipe(title = "Ćwikła"))
        repository.saveRecipe(sampleRecipe(title = "Ananas"))

        val titles = repository.observeRecipes(RecipeFilter(sort = RecipeSort.TITLE_ASC))
            .first()
            .map { it.title }
        assertEquals(listOf("Ananas", "Ćwikła", "Żurek"), titles)
    }

    @Test
    fun `sortuje po czasie rosnaco`() = runTest {
        repository.saveRecipe(sampleRecipe(title = "Wolny", prep = 60, cook = 60))
        repository.saveRecipe(sampleRecipe(title = "Szybki", prep = 5, cook = 5))

        val titles = repository.observeRecipes(RecipeFilter(sort = RecipeSort.TIME_ASC))
            .first()
            .map { it.title }
        assertEquals(listOf("Szybki", "Wolny"), titles)
    }

    // --- usuwanie ---------------------------------------------------------

    @Test
    fun `miekkie usuniecie ukrywa przepis i czysci indeks`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        repository.deleteRecipe(id)

        assertTrue(repository.observeRecipes(RecipeFilter()).first().isEmpty())
        assertTrue(search("szarlotka").isEmpty())
        assertNull(repository.observeRecipe(id).first())
        // Wiersz zostaje jako „nagrobek”, żeby usunięcie dojechało do chmury.
        assertNotNull(db.recipeDao().getRecipe(id))
    }

    @Test
    fun `przywrocenie wraca do listy i do indeksu`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        repository.deleteRecipe(id)
        repository.restoreRecipe(id)

        assertEquals(1, repository.observeRecipes(RecipeFilter()).first().size)
        assertEquals(1, search("szarlotka").size)
    }

    @Test
    fun `duplikat jest osobnym przepisem z wlasnym identyfikatorem zdalnym`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        val copyId = repository.duplicateRecipe(id)!!

        val original = repository.getRecipe(id)!!
        val copy = repository.getRecipe(copyId)!!
        assertTrue(copy.recipe.title.contains("kopia"))
        assertTrue(original.recipe.remoteId != copy.recipe.remoteId)
        assertEquals(original.groups.size, copy.groups.size)
        assertEquals(2, repository.observeRecipes(RecipeFilter()).first().size)
    }

    // --- szybkie akcje ----------------------------------------------------

    @Test
    fun `zmienia ulubione ocene i notatki`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())

        repository.setFavorite(id, true)
        repository.setRating(id, 5)
        repository.setNotes(id, "Dodać więcej cynamonu")

        val loaded = repository.getRecipe(id)!!.recipe
        assertTrue(loaded.isFavorite)
        assertEquals(5, loaded.rating)
        assertEquals("Dodać więcej cynamonu", loaded.notes)
    }

    @Test
    fun `ocena jest ograniczona do zakresu od jednego do pieciu`() = runTest {
        val id = repository.saveRecipe(sampleRecipe())
        repository.setRating(id, 9)
        assertEquals(5, repository.getRecipe(id)!!.recipe.rating)
    }

    @Test
    fun `nowy szkic ma nadany identyfikator zdalny i jedna grupe`() = runTest {
        val draft = repository.newDraft()
        assertEquals(0L, draft.recipe.id)
        assertTrue(draft.recipe.remoteId.isNotBlank())
        assertEquals(1, draft.groups.size)
    }

    @Test
    fun `zapis oznacza przepis jako wymagajacy synchronizacji`() = runTest {
        repository.saveRecipe(sampleRecipe())
        assertEquals(1, db.recipeDao().observeDirtyCount().first())
    }
}
