package app.lily.ai.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Estado da rede (para respeitar "baixar só no Wi-Fi"). */
object Network {
    /** Conectado por uma rede tarifada (dados móveis) ou sem saber. */
    fun metered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
