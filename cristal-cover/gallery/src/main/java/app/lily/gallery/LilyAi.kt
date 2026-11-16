package app.lily.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A ponte com a L.I.L.Y AI: a Gallery não tem IA própria; ela manda a foto (com permissão de leitura
 * só daquela foto) para a L.I.L.Y AI, que devolve os resultados salvos em Pictures/L.I.L.Y.
 */
object LilyAi {
    const val PACKAGE = "app.lily.ai"
    private const val ACTION_IMAGE_TOOL = "app.lily.ai.action.IMAGE_TOOL"

    /** As ações que a L.I.L.Y AI oferece para uma foto (nomes iguais às tarefas da IA). */
    val actions = listOf(
        "ASK" to "Perguntar sobre a foto",
        "DESCRIBE_IMAGE" to "Descrever",
        "OCR" to "Extrair texto",
        "LABELS" to "Identificar objetos",
        "ENHANCE" to "Melhorar foto",
        "DENOISE" to "Remover ruído",
        "UPSCALE" to "Ampliar resolução",
        "REMOVE_BACKGROUND" to "Remover fundo",
    )

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun openTool(context: Context, uri: Uri, mime: String, tool: String) {
        context.startActivity(
            Intent(ACTION_IMAGE_TOOL)
                .setPackage(PACKAGE)
                .setDataAndType(uri, mime.ifBlank { "image/*" })
                .putExtra("tool", tool)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Abre a geração de imagens da L.I.L.Y AI. */
    fun openImageGen(context: Context) {
        context.startActivity(
            Intent().setClassName(PACKAGE, "$PACKAGE.ui.MainActivity").putExtra("route", "imagegen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** O Google Fotos pelo caminho oficial: intents do próprio app (abrir e enviar para backup). */
object GooglePhotos {
    const val PACKAGE = "com.google.android.apps.photos"

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun open(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Manda as fotos para o Google Fotos (ele mostra a tela de envio para o backup). */
    fun send(context: Context, uris: List<Uri>) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        context.startActivity(intent.setType("image/*").setPackage(PACKAGE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
