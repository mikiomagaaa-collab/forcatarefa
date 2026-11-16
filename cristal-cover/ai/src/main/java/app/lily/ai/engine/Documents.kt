package app.lily.ai.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Documentos anexados à conversa, lidos no aparelho: texto puro direto; PDF com o texto embutido
 * (leitor de PDF do Android 15+) e, nas páginas escaneadas, com o OCR do ML Kit.
 */
object Documents {
    data class Extracted(val name: String, val text: String, val pages: Int?, val ocrPages: Int = 0)

    const val MAX_PAGES = 12
    const val MAX_CHARS = 120_000

    suspend fun extract(context: Context, uri: Uri): Extracted {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "documento"
        val mime = resolver.getType(uri).orEmpty()
        return if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) pdf(context, uri, name) else plain(context, uri, name)
    }

    private fun plain(context: Context, uri: Uri, name: String): Extracted {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
            val buffer = CharArray(MAX_CHARS)
            val n = r.read(buffer)
            if (n > 0) String(buffer, 0, n) else ""
        } ?: throw IOException("Não deu para abrir o arquivo")
        return Extracted(name, text.trim(), null)
    }

    private suspend fun pdf(context: Context, uri: Uri, name: String): Extracted {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Não deu para abrir o PDF")
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val out = StringBuilder()
                var ocr = 0
                val count = renderer.pageCount
                for (i in 0 until min(count, MAX_PAGES)) {
                    renderer.openPage(i).use { page ->
                        var text = if (Build.VERSION.SDK_INT >= 35) embedded(page) else ""
                        if (text.isBlank()) {
                            // Página escaneada (imagem): lê com o OCR.
                            val scale = 1600f / page.width.coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1), (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            text = Vision.ocr(bitmap)
                            bitmap.recycle()
                            if (text.isNotBlank()) ocr++
                        }
                        if (text.isNotBlank()) out.append("[Página ${i + 1}]\n").append(text.trim()).append("\n\n")
                    }
                    if (out.length > MAX_CHARS) break
                }
                return Extracted(name, out.toString().trim().take(MAX_CHARS), count, ocr)
            }
        }
    }

    @androidx.annotation.RequiresApi(35)
    private fun embedded(page: PdfRenderer.Page): String =
        runCatching { page.textContents.joinToString("\n") { it.text } }.getOrDefault("")
}
