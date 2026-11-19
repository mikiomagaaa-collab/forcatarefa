package app.lily.gallery.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.lily.gallery.data.MediaItem
import app.lily.gallery.data.MediaRepo
import kotlinx.coroutines.launch

/** De onde vem a lista que o visualizador percorre. */
@Immutable
sealed interface Source {
    data object All : Source
    data class Album(val id: Long) : Source
    data object Favorites : Source
    data object Trash : Source
    data class Search(val query: String) : Source
}

@Immutable
sealed interface Screen {
    data object Home : Screen
    data class Album(val id: Long, val name: String) : Screen
    data object Favorites : Screen
    data object Trash : Screen
    data class Viewer(val source: Source, val startId: Long) : Screen
    data class Editor(val id: Long) : Screen
    /** Uma imagem de fora da biblioteca, aberta por outro app. */
    data class External(val uri: Uri, val mime: String) : Screen
}

/** O que muda o arquivo de mídia passa por uma confirmação do próprio Android. */
interface SystemRequests {
    fun trash(uris: List<Uri>, trashed: Boolean, done: (Boolean) -> Unit)
    fun delete(uris: List<Uri>, done: (Boolean) -> Unit)
    fun favorite(uris: List<Uri>, favorite: Boolean, done: (Boolean) -> Unit)
    fun write(uris: List<Uri>, done: (Boolean) -> Unit)

    /** Com o "gerenciamento de mídia" liberado, o Android não pergunta a cada lixeira ou favorito. */
    fun canManageMedia(): Boolean
    fun openManageMedia()

    /** Pede de novo o acesso às fotos (no acesso parcial, o Android mostra a seleção). */
    fun requestMedia()
}

private class ActivityRequests(private val activity: MainActivity) : SystemRequests {
    override fun trash(uris: List<Uri>, trashed: Boolean, done: (Boolean) -> Unit) =
        activity.launch(MediaStore.createTrashRequest(activity.contentResolver, uris, trashed), done)

    override fun delete(uris: List<Uri>, done: (Boolean) -> Unit) = activity.launch(MediaStore.createDeleteRequest(activity.contentResolver, uris), done)

    override fun favorite(uris: List<Uri>, favorite: Boolean, done: (Boolean) -> Unit) =
        activity.launch(MediaStore.createFavoriteRequest(activity.contentResolver, uris, favorite), done)

    override fun write(uris: List<Uri>, done: (Boolean) -> Unit) = activity.launch(MediaStore.createWriteRequest(activity.contentResolver, uris), done)

    override fun canManageMedia(): Boolean = MediaStore.canManageMedia(activity)

    override fun openManageMedia() {
        runCatching {
            activity.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${activity.packageName}")))
        }
    }

    override fun requestMedia() = activity.requestMedia()
}

val LocalRequests = staticCompositionLocalOf<SystemRequests> { error("sem pedidos") }
val LocalRepo = staticCompositionLocalOf<MediaRepo> { error("sem biblioteca") }

/** Pilha de telas da Gallery. */
@Stable
class GalleryNav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    fun go(s: Screen) {
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replace(s: Screen) {
        stack[stack.lastIndex] = s
    }
}

enum class Access { NONE, PARTIAL, FULL }

class MainActivity : ComponentActivity() {
    private lateinit var repo: MediaRepo
    private val nav = GalleryNav()
    private var access by mutableStateOf(Access.NONE)
    private var pendingResult: ((Boolean) -> Unit)? = null

    private val senderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r: ActivityResult ->
        pendingResult?.invoke(r.resultCode == RESULT_OK)
        pendingResult = null
        lifecycleScope.launch { repo.refresh() }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        updateAccess()
        lifecycleScope.launch { repo.refresh() }
    }

    fun launch(request: PendingIntent, done: (Boolean) -> Unit) {
        pendingResult = done
        senderLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    fun requestMedia() {
        val perms = mutableListOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT >= 34) perms += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun updateAccess() {
        access = when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
            else -> Access.NONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repo = MediaRepo(applicationContext)
        updateAccess()
        if (savedInstanceState == null) handle(intent)
        setContent {
            GalleryTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalRepo provides repo, LocalRequests provides androidx.compose.runtime.remember { ActivityRequests(this) }) {
                    Box(Modifier.fillMaxSize().background(G.colors.background)) {
                        GalleryApp(nav, access)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccess()
        if (access != Access.NONE) {
            repo.observe(lifecycleScope)
            lifecycleScope.launch { repo.refresh() }
        }
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Abrir na Gallery" (a L.I.L.Y AI e outros apps): a foto na biblioteca ou, se for de fora, sozinha. */
    private fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val mime = intent.type ?: contentResolver.getType(uri) ?: "image/*"
        lifecycleScope.launch {
            if (access != Access.NONE) repo.refresh()
            val item: MediaItem? = repo.byUri(uri)
            nav.go(if (item != null) Screen.Viewer(Source.All, item.id) else Screen.External(uri, mime))
        }
    }
}

@Composable
fun GalleryApp(nav: GalleryNav, access: Access) {
    if (access == Access.NONE && nav.current !is Screen.External) {
        PermissionScreen()
        return
    }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    AnimatedContent(
        targetState = nav.current,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "telas",
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(nav, access)
            is Screen.Album -> AlbumScreen(nav, screen)
            Screen.Favorites -> FavoritesScreen(nav)
            Screen.Trash -> TrashScreen(nav)
            is Screen.Viewer -> ViewerScreen(nav, screen)
            is Screen.Editor -> EditorScreen(nav, screen.id)
            is Screen.External -> ExternalViewer(nav, screen)
        }
    }
}
