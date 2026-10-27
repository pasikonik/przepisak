package pl.przepisak.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.DefaultDispatcher
import pl.przepisak.core.util.Outcome
import pl.przepisak.data.prefs.SettingsRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kompresja zdjęć przepisów przed zapisem na dysk (RF-14).
 *
 * Algorytm:
 *  1. Wymiary obrazu odczytujemy przez [BitmapFactory.Options] z
 *     `inJustDecodeBounds = true` — bez ładowania pikseli do pamięci.
 *  2. Na tej podstawie dobieramy `inSampleSize` jako potęgę 2: największą, przy
 *     której dłuższa krawędź po podpróbkowaniu nadal nie schodzi poniżej
 *     docelowego wymiaru (dekoder Androida obsługuje tylko potęgi 2).
 *  3. Dekodujemy z tym `inSampleSize`, a potem dopasowujemy rozmiar dokładnie
 *     przez [Bitmap.createScaledBitmap] (proporcje zachowane) — podpróbkowanie
 *     daje tylko przybliżenie „w dół”.
 *  4. Poprawiamy orientację według EXIF-u: zdjęcia z aparatu bywają zapisane
 *     „na leżąco” z samą flagą obrotu w metadanych, więc bez tej korekty
 *     wyświetlałyby się bokiem lub do góry nogami.
 *  5. Zapisujemy JPEG w pętli jakości (90 → 50, krok 10), aż plik zmieści się
 *     w limicie z ustawień — przewidywalny rozmiar na dysku jest ważniejszy
 *     niż stała jakość.
 */
@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun compress(source: Uri, destination: File): Outcome<File> = withContext(dispatcher) {
        runCompression(destination) { openUriStream(source) }
    }

    suspend fun compress(bytes: ByteArray, destination: File): Outcome<File> = withContext(dispatcher) {
        runCompression(destination) { ByteArrayInputStream(bytes) }
    }

    /**
     * Wspólna ścieżka dla obu przeciążeń — różni je tylko sposób otwarcia strumienia
     * źródłowego, który trzeba otworzyć trzykrotnie (wymiary, dekodowanie, EXIF),
     * bo raz odczytanego `InputStream` nie da się przewinąć.
     */
    private suspend fun runCompression(destination: File, openStream: () -> InputStream): Outcome<File> {
        return try {
            val appSettings = settings.current()
            val bitmap = decodeScaledAndRotated(openStream, appSettings.photoMaxDimensionPx)
            try {
                writeCompressed(bitmap, destination, appSettings.photoTargetKb)
            } finally {
                bitmap.recycle()
            }
            Outcome.Success(destination)
        } catch (e: OutOfMemoryError) {
            // To błąd wirtualnej maszyny (Error), nie wyjątek — trzeba go łapać osobno.
            Outcome.Failure(AppError.ImageProcessingFailed("Za mało pamięci na przetworzenie zdjęcia", e))
        } catch (e: Exception) {
            Outcome.Failure(AppError.ImageProcessingFailed(e.message, e))
        }
    }

    private fun openUriStream(source: Uri): InputStream =
        context.contentResolver.openInputStream(source)
            ?: throw IOException("Nie udało się otworzyć zdjęcia: $source")

    /** Dekoduje bitmapę podpróbkowaną do [maxDimension], doprecyzowaną skalowaniem i obróconą wg EXIF. */
    private fun decodeScaledAndRotated(openStream: () -> InputStream, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream().use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Nieprawidłowy plik obrazu")
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val sampled = openStream().use { stream -> BitmapFactory.decodeStream(stream, null, decodeOptions) }
            ?: throw IOException("Nie udało się zdekodować obrazu")

        val orientation = openStream().use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }

        val scaled = scaleToMaxDimension(sampled, maxDimension)
        if (scaled !== sampled) sampled.recycle()

        val rotated = applyExifOrientation(scaled, orientation)
        if (rotated !== scaled) scaled.recycle()
        return rotated
    }

    /** Największa potęga 2, przy której dłuższa krawędź po podpróbkowaniu nie schodzi poniżej [maxDimension]. */
    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        if (maxDimension <= 0) return 1
        val longerEdge = maxOf(width, height)
        var sample = 1
        while (longerEdge / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longerEdge = maxOf(bitmap.width, bitmap.height)
        if (maxDimension <= 0 || longerEdge <= maxDimension) return bitmap
        val scale = maxDimension.toDouble() / longerEdge
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /** Obrót/odbicie zgodnie ze znacznikiem EXIF — aparaty zapisują piksele „surowo” i tylko flagują orientację. */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Zapisuje JPEG, obniżając jakość co [QUALITY_STEP] (start [START_QUALITY]) aż plik zmieści się w [targetKb]. */
    private fun writeCompressed(bitmap: Bitmap, destination: File, targetKb: Int) {
        val targetBytes = targetKb.toLong() * BYTES_PER_KB
        var quality = START_QUALITY
        var bytes: ByteArray
        while (true) {
            bytes = ByteArrayOutputStream().use { buffer ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
                buffer.toByteArray()
            }
            if (bytes.size <= targetBytes || quality <= MIN_QUALITY) break
            quality -= QUALITY_STEP
        }
        FileOutputStream(destination).use { it.write(bytes) }
    }

    private companion object {
        const val START_QUALITY = 90
        const val MIN_QUALITY = 50
        const val QUALITY_STEP = 10
        const val BYTES_PER_KB = 1024L
    }
}
