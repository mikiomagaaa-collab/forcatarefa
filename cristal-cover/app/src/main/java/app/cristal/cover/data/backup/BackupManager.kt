package app.cristal.cover.data.backup

import android.content.Context
import android.net.Uri
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.data.apps.AppRepository
import app.cristal.cover.data.db.AppPrefDao
import app.cristal.cover.data.db.AppPrefEntity
import app.cristal.cover.data.db.HomeItemEntity
import app.cristal.cover.data.home.HomeRepository
import app.cristal.cover.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupItem(
    val id: Long,
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

@Serializable
data class BackupAppPref(
    val componentKey: String,
    val hidden: Boolean = false,
    val customLabel: String? = null,
    val scalePercent: Int = 100,
    val forceResizable: Boolean = false,
    val skipWarning: Boolean = false,
    val launchCount: Int = 0,
    val lastLaunch: Long = 0,
)

@Serializable
data class BackupFile(
    val app: String = BackupManager.APP_MARKER,
    val format: Int = BackupManager.FORMAT,
    val createdAt: Long = 0,
    val settings: Map<String, String> = emptyMap(),
    val home: List<BackupItem> = emptyList(),
    val appPrefs: List<BackupAppPref> = emptyList(),
)

enum class BackupResult { OK, INVALID_FILE, NEWER_FORMAT, IO_ERROR }

/**
 * Backup e restauração em um único arquivo JSON, escolhido pelo usuário no seletor de arquivos do
 * sistema. Inclui preferências, tela inicial e ajustes por app. A imagem do papel de parede não
 * entra (o arquivo ficaria grande demais); depois de restaurar, basta escolhê-la de novo.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val homeRepository: HomeRepository,
    private val appPrefDao: AppPrefDao,
    private val appRepository: AppRepository,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun export(uri: Uri): BackupResult = withContext(io) {
        try {
            val text = json.encodeToString(BackupFile.serializer(), snapshot())
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext BackupResult.IO_ERROR
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            BackupResult.OK
        } catch (t: Throwable) {
            Logx.e("Falha ao exportar backup", t)
            BackupResult.IO_ERROR
        }
    }

    suspend fun import(uri: Uri): BackupResult = withContext(io) {
        val text = try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext BackupResult.IO_ERROR
            stream.use { input ->
                // Um backup legítimo tem poucos KB; recusa arquivos gigantes antes de alocar tudo.
                val bytes = input.readNBytes(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) return@withContext BackupResult.INVALID_FILE
                String(bytes, Charsets.UTF_8)
            }
        } catch (t: Throwable) {
            Logx.e("Falha ao ler backup", t)
            return@withContext BackupResult.IO_ERROR
        }
        restore(text)
    }

    suspend fun snapshot(): BackupFile = BackupFile(
        createdAt = System.currentTimeMillis(),
        settings = settingsRepository.exportRaw(),
        home = homeRepository.exportAll().map { it.toBackup() },
        appPrefs = appPrefDao.getAll().map { it.toBackup() },
    )

    suspend fun restore(text: String): BackupResult {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (t: Throwable) {
            Logx.w("Arquivo de backup inválido", t)
            return BackupResult.INVALID_FILE
        }
        if (backup.app != APP_MARKER) return BackupResult.INVALID_FILE
        if (backup.format > FORMAT) return BackupResult.NEWER_FORMAT
        return try {
            settingsRepository.importRaw(backup.settings)
            homeRepository.importAll(backup.home.mapNotNull { it.toEntity() })
            appPrefDao.replaceAll(backup.appPrefs.map { it.toEntity() })
            appRepository.requestReload()
            BackupResult.OK
        } catch (t: Throwable) {
            Logx.e("Falha ao restaurar backup", t)
            BackupResult.IO_ERROR
        }
    }

    companion object {
        const val APP_MARKER = "cristal-cover"
        const val FORMAT = 1
        const val MAX_BYTES = 4 * 1024 * 1024
        const val SUGGESTED_NAME = "cristal-cover-backup.json"
    }
}

private fun HomeItemEntity.toBackup() = BackupItem(
    id = id, type = type, container = container, page = page, cellX = cellX, cellY = cellY,
    spanX = spanX, spanY = spanY, componentKey = componentKey, title = title, widgetKind = widgetKind,
)

/** Descarta itens com valores absurdos, para um arquivo corrompido não quebrar a tela inicial. */
private fun BackupItem.toEntity(): HomeItemEntity? {
    if (type !in 0..2 || page !in 0..50 || cellX !in 0..50 || cellY !in 0..50) return null
    if (spanX !in 1..8 || spanY !in 1..8) return null
    return HomeItemEntity(
        id = id, type = type, container = container, page = page, cellX = cellX, cellY = cellY,
        spanX = spanX, spanY = spanY, componentKey = componentKey, title = title?.take(60), widgetKind = widgetKind,
    )
}

private fun AppPrefEntity.toBackup() = BackupAppPref(
    componentKey = componentKey, hidden = hidden, customLabel = customLabel, scalePercent = scalePercent,
    forceResizable = forceResizable, skipWarning = skipWarning, launchCount = launchCount, lastLaunch = lastLaunch,
)

private fun BackupAppPref.toEntity() = AppPrefEntity(
    componentKey = componentKey, hidden = hidden, customLabel = customLabel?.take(60),
    scalePercent = scalePercent.coerceIn(50, 100), forceResizable = forceResizable, skipWarning = skipWarning,
    launchCount = launchCount.coerceAtLeast(0), lastLaunch = lastLaunch,
)
