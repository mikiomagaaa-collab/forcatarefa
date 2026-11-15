package app.cristal.cover

import android.app.Application
import app.cristal.cover.core.Logx
import app.cristal.cover.data.home.HomeMaintenance
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CristalApp : Application() {

    @Inject lateinit var homeMaintenance: HomeMaintenance

    override fun onCreate() {
        super.onCreate()
        // No processo do serviço do Shizuku (":shell") não há UI nem banco de dados para manter.
        if (!isMainProcess()) return
        try {
            homeMaintenance.start()
        } catch (t: Throwable) {
            Logx.e("Falha ao iniciar a manutenção da tela inicial", t)
        }
    }

    private fun isMainProcess(): Boolean = getProcessName() == packageName
}
