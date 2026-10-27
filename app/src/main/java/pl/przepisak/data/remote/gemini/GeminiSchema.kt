package pl.przepisak.data.remote.gemini

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Schemat JSON przekazywany w `generationConfig.responseSchema`, wymuszający na
 * Gemini strukturę zgodną z [GeminiRecipeDto].
 *
 * Gemini rozumie tylko okrojony podzbiór OpenAPI: klucze "type", "properties",
 * "items", "required", "enum", "description", "nullable" — nic więcej (żadnego
 * "additionalProperties" ani "$schema", oba są odrzucane). Typy zapisujemy
 * WIELKIMI literami, jak wymaga tego API.
 */
internal object GeminiSchema {

    private fun stringSchema(
        description: String,
        nullable: Boolean = false,
        enumValues: List<String>? = null,
    ): JsonObject = buildJsonObject {
        put("type", "STRING")
        put("description", description)
        if (nullable) put("nullable", true)
        if (enumValues != null) {
            putJsonArray("enum") { enumValues.forEach { add(it) } }
        }
    }

    /** Wszystkie liczby w [GeminiRecipeDto] są opcjonalne (`Int?`), więc zawsze `nullable`. */
    private fun intSchema(description: String): JsonObject = buildJsonObject {
        put("type", "INTEGER")
        put("description", description)
        put("nullable", true)
    }

    private fun stringArraySchema(description: String): JsonObject = buildJsonObject {
        put("type", "ARRAY")
        put("description", description)
        putJsonObject("items") { put("type", "STRING") }
    }

    /** Odpowiada [GeminiIngredientGroupDto]. */
    private val ingredientGroupSchema: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "description",
            "Grupa składników, np. \"Ciasto\" albo \"Krem\" — brak name oznacza grupę domyślną.",
        )
        putJsonObject("properties") {
            put("name", stringSchema("Nazwa grupy składników, jeśli źródło ją wyróżnia.", nullable = true))
            put(
                "items",
                stringArraySchema(
                    "Linie składników tej grupy, jedna pozycja na linię (ilość, jednostka, nazwa razem, tak jak w źródle).",
                ),
            )
        }
        putJsonArray("required") { add("items") }
    }

    /** Schemat odpowiadający [GeminiRecipeDto] — patrz `GeminiRecipeDto` po znaczenie pól. */
    val recipeResponseSchema: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            put("title", stringSchema("Tytuł przepisu, w oryginalnym języku źródła."))
            put("description", stringSchema("Krótki opis/zajawka przepisu, jeśli źródło go zawiera.", nullable = true))
            put("servings", intSchema("Liczba porcji jako liczba całkowita, jeśli podana wprost w źródle."))
            put(
                "servingsLabel",
                stringSchema(
                    "Opis porcji, gdy nie jest to zwykła liczba, np. \"blaszka 24 cm\" albo \"10 sztuk\".",
                    nullable = true,
                ),
            )
            put("prepTimeMinutes", intSchema("Czas przygotowania w minutach."))
            put("cookTimeMinutes", intSchema("Czas gotowania/pieczenia w minutach."))
            put("totalTimeMinutes", intSchema("Całkowity czas w minutach, TYLKO jeśli podany wprost w źródle — nie sumuj sam."))
            put(
                "difficulty",
                stringSchema(
                    "Poziom trudności, jeśli źródło go określa.",
                    nullable = true,
                    enumValues = listOf("EASY", "MEDIUM", "HARD"),
                ),
            )
            put("category", stringSchema("Kategoria przepisu, np. \"Zupy\", \"Desery\", \"Dania główne\".", nullable = true))
            put("tags", stringArraySchema("Etykiety/tagi przepisu (np. \"wegańskie\", \"na słodko\")."))
            put(
                "ingredientGroups",
                buildJsonObject {
                    put("type", "ARRAY")
                    put(
                        "description",
                        "Składniki podzielone na grupy. Jeśli źródło nie wyróżnia grup, zwróć jedną grupę bez name.",
                    )
                    put("items", ingredientGroupSchema)
                },
            )
            put("steps", stringArraySchema("Kroki przygotowania — jeden krok, jedna czynność, jeden element listy."))
            put("notes", stringSchema("Dodatkowe uwagi/wskazówki z przepisu, jeśli są.", nullable = true))
            put("imageUrl", stringSchema("Adres URL zdjęcia dania, jeśli występuje w źródle.", nullable = true))
        }
        putJsonArray("required") {
            add("title")
            add("ingredientGroups")
            add("steps")
        }
    }
}
