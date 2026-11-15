package app.cristal.cover.data.launch

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cristal.cover.core.safely
import app.cristal.cover.data.display.DisplayDesc
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

enum class CompatLevel { OK, CAUTION, PROBLEM }

enum class CompatReason {
    /** O app só funciona deitado; numa tela quase quadrada ele aparece com faixas ou girado. */
    FIXED_LANDSCAPE,

    /** App antigo (anterior ao Android 7), que não sabe se redimensionar. */
    OLD_TARGET,

    /** O manifesto exige uma tela maior do que a externa. */
    NEEDS_LARGER_SCREEN,

    /** A janela mínima declarada não cabe na tela externa. */
    MIN_WINDOW_TOO_BIG,

    /** O manifesto declara que não suporta telas pequenas. */
    NO_SMALL_SCREENS,

    /** Jogos costumam assumir telas grandes e controles nas bordas. */
    GAME,
}

data class CompatReport(val level: CompatLevel, val reasons: List<CompatReason>) {
    companion object {
        val ok = CompatReport(CompatLevel.OK, emptyList())
    }
}

/**
 * Avalia, só com informações públicas do manifesto, se um app tende a funcionar mal na tela
 * externa. É uma heurística: o Android não expõe a apps comuns o "modo de redimensionamento" real.
 */
@Singleton
class CompatAdvisor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun evaluate(component: ComponentName, cover: DisplayDesc?): CompatReport {
        val info: ActivityInfo = safely("ler activity $component") {
            context.packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } ?: return CompatReport.ok
        return evaluate(
            orientation = info.screenOrientation,
            targetSdk = info.applicationInfo.targetSdkVersion,
            requiresSmallestWidthDp = info.applicationInfo.requiresSmallestWidthDp,
            minWindowWidthPx = info.windowLayout?.minWidth ?: 0,
            minWindowHeightPx = info.windowLayout?.minHeight ?: 0,
            supportsSmallScreens = info.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_SMALL_SCREENS != 0,
            isGame = info.applicationInfo.category == ApplicationInfo.CATEGORY_GAME,
            cover = cover,
        )
    }

    companion object {
        private val LANDSCAPE = setOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
        )

        /** Regra pura, separada para poder ser testada sem Android. */
        fun evaluate(
            orientation: Int,
            targetSdk: Int,
            requiresSmallestWidthDp: Int,
            minWindowWidthPx: Int,
            minWindowHeightPx: Int,
            supportsSmallScreens: Boolean,
            isGame: Boolean,
            cover: DisplayDesc?,
        ): CompatReport {
            val reasons = ArrayList<CompatReason>()
            var level = CompatLevel.OK
            fun flag(reason: CompatReason, severity: CompatLevel) {
                reasons += reason
                if (severity.ordinal > level.ordinal) level = severity
            }

            if (cover != null) {
                val smallestPx = min(cover.widthPx, cover.heightPx)
                val smallestDp = if (cover.densityDpi > 0) smallestPx * 160 / cover.densityDpi else smallestPx
                if (requiresSmallestWidthDp > 0 && requiresSmallestWidthDp > smallestDp) {
                    flag(CompatReason.NEEDS_LARGER_SCREEN, CompatLevel.PROBLEM)
                }
                if (minWindowWidthPx > cover.widthPx || minWindowHeightPx > cover.heightPx) {
                    flag(CompatReason.MIN_WINDOW_TOO_BIG, CompatLevel.PROBLEM)
                }
            }
            if (orientation in LANDSCAPE) flag(CompatReason.FIXED_LANDSCAPE, CompatLevel.CAUTION)
            if (targetSdk < 24) flag(CompatReason.OLD_TARGET, CompatLevel.CAUTION)
            if (!supportsSmallScreens) flag(CompatReason.NO_SMALL_SCREENS, CompatLevel.CAUTION)
            if (isGame) flag(CompatReason.GAME, CompatLevel.CAUTION)
            return CompatReport(level, reasons)
        }
    }
}
