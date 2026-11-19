package app.cristal.cover.ui.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Display
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.home.CellSlot
import app.cristal.cover.data.home.HomeItem
import kotlinx.coroutines.delay

/** Em qual tela esta activity está e se ela é a tela externa. */
@Immutable
data class DisplayContext(val displayId: Int, val onCover: Boolean)

/** Id da tela em que a activity está agora (muda quando o sistema move a tarefa de tela). */
@Composable
fun rememberDisplayId(): Int {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        safely("ler tela da activity") { (context as? Activity)?.display?.displayId } ?: Display.DEFAULT_DISPLAY
    }
}

/** Abre uma Intent a partir da activity, na tela pedida, sem deixar exceções escaparem. */
fun Context.startSafely(intent: Intent, displayId: Int = Display.INVALID_DISPLAY): Boolean {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        val options = if (displayId != Display.INVALID_DISPLAY) {
            android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        } else {
            null
        }
        startActivity(intent, options)
        true
    } catch (e: Exception) {
        Logx.w("Falha ao abrir ${intent.action ?: intent.component}", e)
        try {
            startActivity(intent)
            true
        } catch (inner: Exception) {
            false
        }
    }
}

/** Onde um item arrastado seria solto. */
sealed interface DropTarget {
    data class Cell(val slot: CellSlot, val spanX: Int, val spanY: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
}

/** Estado do arrastar-e-soltar da tela inicial. As posições são em pixels, no espaço da raiz. */
@Stable
class DragController {
    var item by mutableStateOf<HomeItem?>(null)
        private set
    var fromDock by mutableStateOf(false)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set

    /** `true` depois que o dedo se afastou o suficiente do ponto do toque longo. */
    var moving by mutableStateOf(false)
        private set

    private var origin = Offset.Zero

    /** Área das páginas e do dock, informadas pelo layout. */
    var gridBounds: Rect = Rect.Zero
    var dockBounds: Rect = Rect.Zero

    fun begin(item: HomeItem, fromDock: Boolean, at: Offset) {
        this.item = item
        this.fromDock = fromDock
        origin = at
        position = at
        moving = false
    }

    /** Devolve `true` no instante em que o arrasto "de verdade" começa. */
    fun moveBy(delta: Offset, slopPx: Float): Boolean {
        if (item == null) return false
        position += delta
        if (!moving && (position - origin).getDistance() > slopPx) {
            moving = true
            return true
        }
        return false
    }

    fun finish() {
        item = null
        moving = false
    }
}

/** Alvo do menu de contexto. */
sealed interface MenuTarget {
    data class Home(val item: HomeItem, val inDock: Boolean) : MenuTarget
    data class FolderChild(val item: HomeItem.App, val folderId: Long) : MenuTarget
    data class DrawerApp(val app: AppInfo) : MenuTarget
}

@Immutable
data class MenuState(val target: MenuTarget, val anchor: Offset)

@Immutable
class MenuAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Mensagem curta no rodapé, com ação opcional. */
@Stable
class SnackbarController {
    var message by mutableStateOf<String?>(null)
        private set
    var actionLabel by mutableStateOf<String?>(null)
        private set
    var action: (() -> Unit)? = null
        private set
    fun set(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        message = text
        this.actionLabel = actionLabel
        this.action = action
    }

    fun clear() {
        message = null
        actionLabel = null
        action = null
    }

    suspend fun showAndWait(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        set(text, actionLabel, action)
        delay(if (action != null) 6_000 else 3_200)
        clear()
    }
}

/**
 * Margens seguras da tela, com um tratamento especial para recortes de câmera no canto inferior
 * (o caso da tela externa do Razr): se a faixa ao lado das câmeras for larga o bastante, o dock
 * vai para lá em vez de desperdiçar a faixa inteira.
 */
@Immutable
data class SafeLayout(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
    /** Quando não nulo, o dock deve ser desenhado nesta faixa, fora da área segura. */
    val dockBand: DockBand? = null,
)

@Immutable
data class DockBand(val offsetX: Dp, val width: Dp, val height: Dp, val bottomMargin: Dp)

@Composable
fun rememberSafeLayout(useCutoutArea: Boolean): SafeLayout {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val view = LocalView.current
    val safe = WindowInsets.safeDrawing
    val cutout = WindowInsets.displayCutout
    val nav = WindowInsets.navigationBars

    val topPx = safe.getTop(density)
    val bottomPx = safe.getBottom(density)
    val leftPx = safe.getLeft(density, direction)
    val rightPx = safe.getRight(density, direction)
    val cutoutBottomPx = cutout.getBottom(density)
    val navBottomPx = nav.getBottom(density)

    return remember(topPx, bottomPx, leftPx, rightPx, cutoutBottomPx, navBottomPx, useCutoutArea, view.width, view.height) {
        with(density) {
            var band: DockBand? = null
            val width = view.width
            val height = view.height
            if (useCutoutArea && cutoutBottomPx > 0 && width > 0 && height > 0) {
                val rects = safely("ler recortes da tela") { view.rootWindowInsets?.displayCutout?.boundingRects }.orEmpty()
                // Recorte que encosta na borda de baixo.
                val bottomRect = rects.filter { it.bottom >= height - 4 && it.height() > 0 }.maxByOrNull { it.height() }
                if (bottomRect != null) {
                    val freeLeft = bottomRect.left - leftPx
                    val freeRight = width - rightPx - bottomRect.right
                    val useLeft = freeLeft >= freeRight
                    val free = if (useLeft) freeLeft else freeRight
                    val bandHeight = cutoutBottomPx - navBottomPx
                    if (free >= width * 0.42f && bandHeight >= 56.dp.toPx()) {
                        val margin = 10.dp.toPx()
                        val x = if (useLeft) leftPx + margin else bottomRect.right + margin
                        band = DockBand(
                            offsetX = x.toDp(),
                            width = (free - 2 * margin).toDp(),
                            height = bandHeight.toDp().coerceAtMost(76.dp),
                            bottomMargin = navBottomPx.toDp() + 6.dp,
                        )
                    }
                }
            }
            SafeLayout(
                top = topPx.toDp(),
                bottom = bottomPx.toDp(),
                start = leftPx.toDp(),
                end = rightPx.toDp(),
                dockBand = band,
            )
        }
    }
}

/** Remove acentos e caixa, para a busca achar "calculadora" digitando "calc" ou "Câmera" com "camera". */
fun normalizeForSearch(text: String): String {
    val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) out.append(ch.lowercaseChar())
    }
    return out.toString()
}

/** Filtra e ordena apps pela busca: primeiro os que começam com o termo, depois os que o contêm. */
fun filterApps(apps: List<AppInfo>, query: String, labelOf: (AppInfo) -> String = { it.label }): List<AppInfo> {
    val q = normalizeForSearch(query.trim())
    if (q.isEmpty()) return apps
    val starts = ArrayList<AppInfo>()
    val wordStarts = ArrayList<AppInfo>()
    val contains = ArrayList<AppInfo>()
    for (app in apps) {
        val label = normalizeForSearch(labelOf(app))
        when {
            label.startsWith(q) -> starts += app
            label.split(' ', '-', '.').any { it.startsWith(q) } -> wordStarts += app
            label.contains(q) || app.packageName.contains(q) -> contains += app
        }
    }
    return starts + wordStarts + contains
}
