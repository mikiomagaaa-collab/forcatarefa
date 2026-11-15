package app.cristal.cover.data.control

import androidx.datastore.preferences.core.stringPreferencesKey
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.Logx
import app.cristal.cover.data.prefs.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/**
 * Layouts da Liquid Control Center, um para cada tela (interna e externa), guardados como JSON
 * nas preferências (assim entram no backup junto com o resto).
 */
@Singleton
class ControlCenterRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val json: Json,
    @AppScope scope: CoroutineScope,
) {
    val inner: StateFlow<ControlLayout> = flowFor(onCover = false, scope)
    val cover: StateFlow<ControlLayout> = flowFor(onCover = true, scope)

    fun layout(onCover: Boolean): StateFlow<ControlLayout> = if (onCover) cover else inner

    private fun flowFor(onCover: Boolean, scope: CoroutineScope): StateFlow<ControlLayout> =
        settingsRepository.raw
            .map { prefs -> decode(prefs[key(onCover)], onCover) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, ControlPreset.LIQUID_FLIP.build(onCover))

    private fun decode(raw: String?, onCover: Boolean): ControlLayout {
        if (raw.isNullOrBlank()) return ControlPreset.LIQUID_FLIP.build(onCover)
        return try {
            json.decodeFromString(ControlLayout.serializer(), raw).cleaned()
        } catch (t: Throwable) {
            Logx.w("Layout da central de controle inválido; usando o padrão", t)
            ControlPreset.LIQUID_FLIP.build(onCover)
        }
    }

    suspend fun save(onCover: Boolean, layout: ControlLayout) {
        val encoded = json.encodeToString(ControlLayout.serializer(), layout.cleaned())
        settingsRepository.set(key(onCover), encoded)
    }

    suspend fun applyPreset(onCover: Boolean, preset: ControlPreset) {
        val keepNotifications = layout(onCover).value.notificationsPage
        save(onCover, preset.build(onCover).copy(notificationsPage = keepNotifications))
    }

    private fun key(onCover: Boolean) = if (onCover) KEY_COVER else KEY_INNER

    private companion object {
        val KEY_INNER = stringPreferencesKey("control_center_inner")
        val KEY_COVER = stringPreferencesKey("control_center_cover")
    }
}
