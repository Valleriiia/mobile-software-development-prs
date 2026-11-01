package app.energymonitor.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/** Запит не надсилався, бо пристрій не має підключення до мережі. */
class NoNetworkException : IOException("Немає підключення до мережі")

interface NetworkMonitor {
    fun isOnline(): Boolean
}

object AlwaysOnline : NetworkMonitor {
    override fun isOnline(): Boolean = true
}

/**
 * Перевіряє наявність мережі через ConnectivityManager до того, як щось
 * надсилати: офлайн немає сенсу чекати таймаут і повторювати запит.
 * Потрібен дозвіл ACCESS_NETWORK_STATE у маніфесті.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {

    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean = try {
        val network = manager.activeNetwork
        val capabilities = if (network == null) null else manager.getNetworkCapabilities(network)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: SecurityException) {
        // Дозвіл не видано: краще спробувати запит, ніж заблокувати застосунок.
        true
    }
}
