package pl.przepisak.data.media

import android.content.Context
import android.net.Uri
import pl.przepisak.domain.model.PhotoRef
import java.io.File
import java.util.UUID

/**
 * Ścieżki plików zdjęć przepisów.
 *
 * Same funkcje, bez wstrzykiwania zależności — z tych samych metod korzysta
 * też warstwa Compose (np. przy budowaniu modelu dla Coila), gdzie Hilt nie
 * zawsze jest pod ręką, a `Context` i tak trzeba mieć w zasięgu.
 */
object PhotoPaths {

    /** Katalog na skompresowane zdjęcia; zakładany przy pierwszym użyciu. */
    fun photosDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(photosDir(context), name)

    fun exists(context: Context, name: String?): Boolean =
        !name.isNullOrBlank() && file(context, name).isFile

    /**
     * Model źródła obrazu dla Coila.
     *
     * Kolejność ma znaczenie: aplikacja jest offline-first, więc najpierw
     * sprawdzamy plik lokalny — ma się wyświetlić bez sieci. Adres źródłowy
     * z importu ([PhotoRef.sourceUrl]) to tylko zapasowa opcja, dopóki zdjęcie
     * nie zostanie pobrane na dysk (patrz `ImportRepository`).
     */
    fun coilModel(context: Context, photo: PhotoRef): Any? {
        val local = photo.localFileName
        // Zwracamy napis `file:///…`, a nie obiekt File: napisy i adresy URI obsługuje
        // każda wersja Coila, więc nie zależymy od tego, jakie typy danych akceptuje
        // konkretne wydanie biblioteki.
        if (local != null && exists(context, local)) {
            return Uri.fromFile(file(context, local)).toString()
        }
        return photo.sourceUrl?.takeIf { it.isNotBlank() }
    }

    /** Losowa, unikalna nazwa pliku — kolizje przy imporcie / robieniu wielu zdjęć są wykluczone. */
    fun newFileName(prefix: String = "photo"): String = "${prefix}_${UUID.randomUUID()}.jpg"
}
