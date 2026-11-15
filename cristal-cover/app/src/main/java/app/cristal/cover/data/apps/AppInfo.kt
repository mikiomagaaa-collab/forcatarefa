package app.cristal.cover.data.apps

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** Um app que pode ser aberto pelo launcher (uma activity LAUNCHER de um perfil). */
@Immutable
data class AppInfo(
    /** Identificador estável: "pacote/classe#serialDoUsuário". */
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    /** Ícone quadrado, sem máscara; a forma é aplicada na hora de desenhar. */
    val icon: ImageBitmap,
    /** Glifo monocromático (ícone temático), quando o app oferece. */
    val monoIcon: ImageBitmap?,
    /** `false` para ícones de pacotes de ícones, que já vêm com forma própria. */
    val maskable: Boolean,
    val isSystem: Boolean,
    val isWorkProfile: Boolean,
) {
    val packageName: String get() = component.packageName

    companion object {
        fun keyOf(component: ComponentName, userSerial: Long): String =
            "${component.packageName}/${component.className}#$userSerial"

        /** Pacote contido em uma chave gerada por [keyOf]. */
        fun packageOfKey(key: String): String = key.substringBefore('/')
    }
}
