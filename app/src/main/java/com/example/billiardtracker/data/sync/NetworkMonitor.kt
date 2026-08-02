package com.example.billiardtracker.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Реактивный источник флага «есть ли интернет». SyncManager подписывается
 * и триггерит sync на true→любое-состояние переходе.
 */
class NetworkMonitor(context: Context) {
    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(currentOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _online.value = true }
        override fun onLost(network: Network) { _online.value = currentOnline() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    init {
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, callback)
        } catch (e: Throwable) {
            // Некоторые ROM могут кинуть SecurityException/RuntimeException
            // даже с permission. Дэградим в always-online — sync будет
            // полагаться на explicit kickDrain() из репо.
            _online.value = true
        }
    }

    private fun currentOnline(): Boolean = try {
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Throwable) { true }
}
