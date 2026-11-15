package app.cristal.cover.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Tipos de item da tela inicial. */
object ItemType {
    const val APP = 0
    const val FOLDER = 1
    const val WIDGET = 2
}

/** Contêineres fixos. Valores positivos são o id da pasta que contém o item. */
object Container {
    const val DESKTOP = -100L
    const val DOCK = -101L
}

/**
 * Um item da tela inicial (app, pasta ou widget embutido).
 *
 * - Na área de trabalho, [page]/[cellX]/[cellY] indicam a célula e [spanX]/[spanY] o tamanho.
 * - No dock e dentro de pastas, só [cellX] é usado, como posição na fila.
 */
@Entity(tableName = "home_items", indices = [Index("container"), Index("componentKey")])
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val container: Long,
    val page: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val componentKey: String? = null,
    val title: String? = null,
    val widgetKind: String? = null,
)

/** Preferências por app: ocultar, escala na tela externa, compatibilidade e uso. */
@Entity(tableName = "app_prefs")
data class AppPrefEntity(
    @PrimaryKey val componentKey: String,
    val hidden: Boolean = false,
    val customLabel: String? = null,
    /** Escala em porcentagem aplicada à densidade da tela externa (100 = sem escala). */
    val scalePercent: Int = 100,
    val forceResizable: Boolean = false,
    /** O usuário pediu para não ver mais o aviso de compatibilidade deste app. */
    val skipWarning: Boolean = false,
    val launchCount: Int = 0,
    val lastLaunch: Long = 0,
)
