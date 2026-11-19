package app.lily.keyboard

import android.app.Application
import app.lily.keyboard.core.KeyboardStore
import app.lily.keyboard.engine.LanguageModels

class LilyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KeyboardStore.get(this)
        // Os dicionários carregam em segundo plano; até lá o teclado digita sem sugestões.
        LanguageModels.get(this).preload(KeyboardStore.get(this).settings.value.language)
    }
}
