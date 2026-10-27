package pl.przepisak.core.util

import javax.inject.Qualifier

/** Dispatcher dla operacji I/O (baza, pliki, sieć). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher dla obliczeń (parsowanie, kompresja, skalowanie). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Zakres korutyn żyjący tak długo, jak proces aplikacji. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
