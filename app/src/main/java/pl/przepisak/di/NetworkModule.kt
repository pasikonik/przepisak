package pl.przepisak.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Wspólna konfiguracja JSON-a.
     *
     * `ignoreUnknownKeys` jest tu obowiązkowe: odpowiedzi Gemini i dane
     * Schema.org zawierają pola, których nie modelujemy, a import nie może się
     * z tego powodu wywalać.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            // Gemini potrafi myśleć kilkanaście sekund nad długą stroną.
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
        followRedirects = true
    }
}
