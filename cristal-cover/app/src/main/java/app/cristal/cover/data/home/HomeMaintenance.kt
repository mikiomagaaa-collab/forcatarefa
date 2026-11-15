package app.cristal.cover.data.home

import app.cristal.cover.core.AppScope
import app.cristal.cover.data.apps.AppRepository
import app.cristal.cover.data.prefs.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tarefas de manutenção da tela inicial que precisam acontecer mesmo sem nenhuma tela aberta:
 * criar o layout inicial, reorganizar quando a grade muda e limpar atalhos de apps desinstalados.
 */
@Singleton
class HomeMaintenance @Inject constructor(
    private val homeRepository: HomeRepository,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    @AppScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            appRepository.loaded.first { it }
            homeRepository.ensureDefaultLayout(appRepository.apps.value)
        }
        scope.launch {
            // Garante que o primeiro valor veio do disco antes de observar mudanças.
            settingsRepository.current()
            settingsRepository.settings
                .map { it.gridColumns to it.gridRows }
                .distinctUntilChanged()
                .drop(1)
                .collect { (cols, rows) -> homeRepository.reflow(cols, rows) }
        }
        scope.launch {
            settingsRepository.current()
            settingsRepository.settings
                .map { it.dockCount }
                .distinctUntilChanged()
                .drop(1)
                .collect { homeRepository.trimDock(it) }
        }
        scope.launch {
            appRepository.removedPackages.collect { homeRepository.removePackage(it) }
        }
    }
}
