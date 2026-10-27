package pl.przepisak.data.remote.web

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pl.przepisak.core.util.AppError
import pl.przepisak.core.util.IoDispatcher
import pl.przepisak.core.util.Outcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pobieranie stron i plików potrzebnych do importu.
 *
 * Podajemy nagłówki typowej przeglądarki mobilnej. Bez tego spora część serwisów
 * kulinarnych odpowiada błędem 403 albo zwraca stronę z zapytaniem o zgody, w której
 * nie ma treści przepisu.
 */
@Singleton
class KtorHtmlFetcher @Inject constructor(
    private val httpClient: HttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) : HtmlFetcher {

    override suspend fun fetch(url: String): Outcome<FetchedPage> = withContext(io) {
        request(url) { response ->
            val body = response.bodyAsText()
            if (body.length > MAX_BODY_CHARS) {
                // Strony reklamowe potrafią mieć megabajty skryptów. Obcinamy,
                // bo parser i tak interesuje tylko początek dokumentu.
                FetchedPage(
                    requestedUrl = url,
                    finalUrl = response.call.request.url.toString(),
                    html = body.take(MAX_BODY_CHARS),
                    contentType = response.headers[HttpHeaders.ContentType],
                )
            } else {
                FetchedPage(
                    requestedUrl = url,
                    finalUrl = response.call.request.url.toString(),
                    html = body,
                    contentType = response.headers[HttpHeaders.ContentType],
                )
            }
        }
    }

    override suspend fun fetchText(url: String): Outcome<String> = withContext(io) {
        request(url) { it.bodyAsText().take(MAX_BODY_CHARS) }
    }

    override suspend fun fetchBytes(url: String): Outcome<ByteArray> = withContext(io) {
        request(url) { response -> response.readRawBytes() }
    }

    private suspend fun <T> request(url: String, transform: suspend (HttpResponse) -> T): Outcome<T> =
        try {
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.AcceptLanguage, "pl-PL,pl;q=0.9,en;q=0.8")
                header(
                    HttpHeaders.Accept,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                )
            }
            val status = response.status.value
            if (status in 200..299) {
                Outcome.Success(transform(response))
            } else {
                Outcome.Failure(AppError.Http(status))
            }
        } catch (e: java.io.IOException) {
            Outcome.Failure(AppError.NoNetwork(e))
        } catch (e: Exception) {
            Outcome.Failure(AppError.Unknown(e))
        }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        /** 5 MB tekstu wystarcza na każdą sensowną stronę z przepisem. */
        const val MAX_BODY_CHARS = 5 * 1024 * 1024
    }
}
