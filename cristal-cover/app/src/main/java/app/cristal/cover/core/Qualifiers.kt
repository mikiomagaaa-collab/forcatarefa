package app.cristal.cover.core

import javax.inject.Qualifier

/** Dispatcher para E/S (disco, rede, IPC). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher para trabalho de CPU (renderizar ícones, ordenar listas). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Escopo que vive enquanto o processo do app viver. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
