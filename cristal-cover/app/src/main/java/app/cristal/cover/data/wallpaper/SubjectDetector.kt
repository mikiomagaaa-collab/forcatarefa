package app.cristal.cover.data.wallpaper

import android.content.Context
import android.graphics.Bitmap
import app.cristal.cover.core.Logx
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Resultado da detecção do objeto principal de uma foto. */
sealed interface SubjectResult {
    /** [foreground] tem o mesmo tamanho da foto; fora do objeto, os pixels são transparentes. */
    class Found(val foreground: Bitmap) : SubjectResult
    data object NoSubject : SubjectResult
    /** O modelo não está disponível (sem Google Play Services ou sem conseguir baixar o módulo). */
    data class Unavailable(val reason: String) : SubjectResult
}

/**
 * Recorta o objeto principal de uma foto com a segmentação de assunto do ML Kit.
 *
 * Tudo roda no aparelho. O modelo não vem no APK: o Google Play Services baixa o módulo uma vez
 * (alguns MB) e depois funciona offline.
 */
class SubjectDetector(private val context: Context) {

    suspend fun detect(bitmap: Bitmap): SubjectResult {
        val play = runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) }.getOrNull()
        if (play != null && play != ConnectionResult.SUCCESS && play != ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED) {
            return SubjectResult.Unavailable("O Google Play Services não está disponível neste aparelho (código $play).")
        }
        val segmenter: SubjectSegmenter = try {
            SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        } catch (t: Throwable) {
            Logx.w("Segmentação de assunto indisponível", t)
            return SubjectResult.Unavailable("Não foi possível iniciar o recorte (${describe(t)}).")
        }
        try {
            if (!ensureModule(segmenter)) {
                return SubjectResult.Unavailable(
                    "Não foi possível baixar o módulo de recorte do Google Play Services. Ligue a internet e toque em \"Recortar objeto\" de novo.",
                )
            }
            val result = try {
                segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
            } catch (t: Throwable) {
                Logx.w("Falha ao recortar o objeto", t)
                return SubjectResult.Unavailable("O recorte falhou (${describe(t)}). Tente de novo com internet ligada na primeira vez.")
            }
            val fg = result.foregroundBitmap ?: return SubjectResult.NoSubject
            return if (coverage(fg) < MIN_COVERAGE) SubjectResult.NoSubject else SubjectResult.Found(fg)
        } finally {
            runCatching { segmenter.close() }
        }
    }

    /** Garante que o módulo do modelo esteja instalado, pedindo o download se preciso. */
    private suspend fun ensureModule(segmenter: SubjectSegmenter): Boolean {
        val client = ModuleInstall.getClient(context)
        val available = runCatching { client.areModulesAvailable(segmenter).await().areModulesAvailable() }.getOrDefault(false)
        if (available) return true
        val done = CompletableDeferred<Boolean>()
        val listener = InstallStatusListener { update: ModuleInstallStatusUpdate ->
            when (update.installState) {
                ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> done.complete(true)
                ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> done.complete(false)
                else -> Unit
            }
        }
        val request = ModuleInstallRequest.newBuilder().addApi(segmenter).setListener(listener).build()
        val response = runCatching { client.installModules(request).await() }.getOrNull() ?: return false
        if (response.areModulesAlreadyInstalled()) return true
        val ok = withTimeoutOrNull(INSTALL_TIMEOUT_MS) { done.await() } ?: false
        runCatching { client.unregisterListener(listener) }
        return ok
    }

    /** Nome curto do erro, para o usuário poder relatar o que aconteceu. */
    private fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return listOfNotNull(root.javaClass.simpleName, root.message?.take(120)).joinToString(": ")
    }

    /** Fração dos pixels que pertencem ao objeto, medida numa grade esparsa. */
    private fun coverage(bitmap: Bitmap): Float {
        val stepX = (bitmap.width / 48).coerceAtLeast(1)
        val stepY = (bitmap.height / 48).coerceAtLeast(1)
        var total = 0
        var hit = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                total++
                if ((bitmap.getPixel(x, y) ushr 24) > 128) hit++
                x += stepX
            }
            y += stepY
        }
        return if (total == 0) 0f else hit.toFloat() / total
    }

    private companion object {
        const val MIN_COVERAGE = 0.015f
        const val INSTALL_TIMEOUT_MS = 120_000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    addOnCanceledListener { cont.cancel() }
}
