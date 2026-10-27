package pl.przepisak.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * Indeks pełnotekstowy przepisów (RF-03).
 *
 * Tabela jest niezależna (bez `contentEntity`), bo indeksujemy dane z kilku tabel
 * naraz — tytuł, opis, składniki i tagi. Repozytorium przebudowuje wiersz w tej
 * samej transakcji, w której zapisuje przepis, więc indeks nie może się rozjechać.
 *
 * Do kolumn trafia tekst już znormalizowany (małe litery, bez diakrytyków) —
 * patrz `TextNormalizer`. Dzięki temu „lodowka” znajduje „lodówka” niezależnie od
 * wersji SQLite w danym Androidzie.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "recipe_fts")
data class RecipeFtsEntity(
    /** Musi nazywać się `rowid` — Room wiąże po nim wiersz FTS z przepisem. */
    @PrimaryKey @ColumnInfo(name = "rowid") val recipeId: Long,
    val title: String,
    val description: String,
    val ingredients: String,
    val tags: String,
    val category: String,
    val notes: String,
)
