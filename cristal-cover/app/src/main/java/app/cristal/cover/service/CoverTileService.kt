package app.cristal.cover.service

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.launcher.LauncherActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Bloco dos Ajustes rápidos que abre o Cristal. É uma porta de entrada para a tela externa que
 * não depende do painel de apps da Motorola: puxe os ajustes rápidos na tela externa e toque nele.
 */
@AndroidEntryPoint
class CoverTileService : TileService() {

    @Inject lateinit var displayRepository: CoverDisplayRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onStartListening() {
        super.onStartListening()
        safely("atualizar bloco") {
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        try {
            val intent = Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic()
            // Se a tela externa está ligada, o usuário está nela: abre lá. Senão, na tela atual.
            val cover = displayRepository.currentCover(settingsRepository.settings.value.coverDisplayOverride)
            if (cover != null && cover.isOn && !cover.isDefault) options.launchDisplayId = cover.id
            if (Build.VERSION.SDK_INT >= 34) {
                val pending = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, options.toBundle(),
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        } catch (t: Throwable) {
            Logx.w("Não foi possível abrir o Cristal pelo bloco", t)
        }
    }
}
