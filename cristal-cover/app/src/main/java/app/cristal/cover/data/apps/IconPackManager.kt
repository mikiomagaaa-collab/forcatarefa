package app.cristal.cover.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

/** Um pacote de ícones já lido: mapeia componente → nome do drawable dentro do pacote. */
class IconPack(
    val packageName: String,
    private val resources: Resources,
    private val mapping: Map<String, String>,
) {
    val size: Int get() = mapping.size

    @Suppress("DiscouragedApi")
    fun drawableFor(component: ComponentName): Drawable? {
        val name = mapping[component.flattenToString()]
            ?: mapping[component.flattenToShortString()]
            ?: return null
        return safely("carregar ícone do pacote") {
            val id = resources.getIdentifier(name, "drawable", packageName)
            if (id == 0) null else resources.getDrawable(id, null)
        }
    }
}

/**
 * Suporte a pacotes de ícones no formato "appfilter.xml", usado por praticamente todos os pacotes
 * da Play Store (ADW, Nova, Apex, Lawnchair etc.).
 */
@Singleton
class IconPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm: PackageManager get() = context.packageManager

    fun installedPacks(): List<IconPackInfo> {
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in THEME_ACTIONS) {
            val matches = safely("procurar pacotes de ícones") {
                pm.queryIntentActivities(Intent(action), PackageManager.ResolveInfoFlags.of(0))
            } ?: continue
            for (info in matches) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg in found) continue
                val label = safely("rótulo do pacote de ícones") {
                    info.activityInfo.applicationInfo.loadLabel(pm).toString()
                } ?: pkg
                found[pkg] = IconPackInfo(pkg, label)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** Lê o pacote. Devolve `null` se não estiver instalado ou não tiver um appfilter válido. */
    @Suppress("DiscouragedApi")
    fun load(packageName: String): IconPack? {
        if (packageName.isBlank()) return null
        return safely("ler pacote de ícones $packageName") {
            val res = pm.getResourcesForApplication(packageName)
            val mapping = HashMap<String, String>()
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            if (xmlId != 0) {
                res.getXml(xmlId).use { parse(it, mapping) }
            } else {
                res.assets.open("appfilter.xml").use { stream ->
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(stream, "utf-8")
                    parse(parser, mapping)
                }
            }
            if (mapping.isEmpty()) null else IconPack(packageName, res, mapping)
        }
    }

    private fun parse(parser: XmlPullParser, out: MutableMap<String, String>) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                    // Formato: ComponentInfo{pacote/classe}
                    val start = component.indexOf('{')
                    val end = component.lastIndexOf('}')
                    if (start >= 0 && end > start) {
                        val flat = component.substring(start + 1, end)
                        ComponentName.unflattenFromString(flat)?.let { cn ->
                            out.putIfAbsent(cn.flattenToString(), drawable)
                        }
                    }
                }
            }
            event = parser.next()
        }
    }

    private companion object {
        val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "app.lawnchair.icons.THEMED_ICON",
        )
    }
}
