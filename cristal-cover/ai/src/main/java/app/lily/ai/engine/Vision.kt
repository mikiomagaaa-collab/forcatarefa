package app.lily.ai.engine

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Espera uma tarefa do Google Play services sem bloquear. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

data class Label(val text: String, val confidence: Float)

/** Os recursos de visão do ML Kit, todos no aparelho. */
object Vision {
    suspend fun ocr(bitmap: Bitmap): String {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            client.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
        } finally {
            client.close()
        }
    }

    suspend fun labels(bitmap: Bitmap): List<Label> {
        val client = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
        return try {
            client.process(InputImage.fromBitmap(bitmap, 0)).await()
                .map { Label(LabelNames.pt(it.text), it.confidence) }
                .sortedByDescending { it.confidence }
        } finally {
            client.close()
        }
    }

    /** O objeto principal com fundo transparente (null se não achou um). */
    suspend fun removeBackground(bitmap: Bitmap): Bitmap? {
        val client = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        return try {
            client.process(InputImage.fromBitmap(bitmap, 0)).await().foregroundBitmap
        } finally {
            client.close()
        }
    }

    /** Código do idioma (BCP-47) ou "und" se não deu para saber. */
    suspend fun language(text: String): String {
        val client = LanguageIdentification.getClient()
        return try {
            client.identifyLanguage(text).await()
        } finally {
            client.close()
        }
    }
}

/** Tradução no aparelho (ML Kit). O pacote do idioma é baixado na primeira vez. */
object Translator {
    val languages = listOf(
        TranslateLanguage.PORTUGUESE to "Português",
        TranslateLanguage.ENGLISH to "Inglês",
        TranslateLanguage.SPANISH to "Espanhol",
        TranslateLanguage.FRENCH to "Francês",
        TranslateLanguage.ITALIAN to "Italiano",
        TranslateLanguage.GERMAN to "Alemão",
        TranslateLanguage.JAPANESE to "Japonês",
        TranslateLanguage.KOREAN to "Coreano",
        TranslateLanguage.CHINESE to "Chinês",
    )

    fun name(code: String): String = languages.firstOrNull { it.first == code }?.second ?: code

    suspend fun translate(text: String, target: String, wifiOnly: Boolean): String {
        val detected = Vision.language(text).takeIf { it != "und" }?.substringBefore('-') ?: TranslateLanguage.PORTUGUESE
        val source = TranslateLanguage.fromLanguageTag(detected) ?: TranslateLanguage.PORTUGUESE
        if (source == target) return text
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        return try {
            val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
            translator.downloadModelIfNeeded(conditions).await()
            translator.translate(text).await()
        } finally {
            translator.close()
        }
    }

    /** Baixa o pacote de um idioma (com "só Wi-Fi", espera o Wi-Fi). */
    suspend fun download(language: String, wifiOnly: Boolean) {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        RemoteModelManager.getInstance().download(TranslateRemoteModel.Builder(language).build(), conditions).await()
    }

    /** Pacotes de idioma já baixados (para o Model Hub listar e apagar). */
    suspend fun downloaded(): Set<String> =
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()

    suspend fun delete(language: String) {
        RemoteModelManager.getInstance().deleteDownloadedModel(TranslateRemoteModel.Builder(language).build()).await()
    }
}

/** Os rótulos do ML Kit vêm em inglês; os mais comuns em português. */
object LabelNames {
    private val pt = mapOf(
        "Person" to "Pessoa", "Smile" to "Sorriso", "Dog" to "Cachorro", "Cat" to "Gato", "Food" to "Comida",
        "Sky" to "Céu", "Cloud" to "Nuvem", "Tree" to "Árvore", "Plant" to "Planta", "Flower" to "Flor",
        "Car" to "Carro", "Vehicle" to "Veículo", "Building" to "Prédio", "Road" to "Rua", "Beach" to "Praia",
        "Sea" to "Mar", "Water" to "Água", "Mountain" to "Montanha", "Sunset" to "Pôr do sol", "Night" to "Noite",
        "Room" to "Ambiente", "Table" to "Mesa", "Chair" to "Cadeira", "Glasses" to "Óculos", "Hand" to "Mão",
        "Screenshot" to "Captura de tela", "Text" to "Texto", "Paper" to "Papel", "Book" to "Livro", "Phone" to "Celular",
        "Computer" to "Computador", "Musical instrument" to "Instrumento musical", "Concert" to "Show", "Crowd" to "Multidão",
        "Event" to "Evento", "Party" to "Festa", "Sports" to "Esporte", "Bird" to "Pássaro", "Fun" to "Diversão",
        "Selfie" to "Selfie", "Shoe" to "Sapato", "Jacket" to "Jaqueta", "Hat" to "Chapéu", "Toy" to "Brinquedo",
        "Cake" to "Bolo", "Fruit" to "Fruta", "Vegetable" to "Verdura", "Pizza" to "Pizza", "Coffee" to "Café",
        "Drink" to "Bebida", "Bridge" to "Ponte", "Lake" to "Lago", "River" to "Rio", "Snow" to "Neve", "Team" to "Equipe",
    )

    fun pt(name: String): String = pt[name] ?: name
}
