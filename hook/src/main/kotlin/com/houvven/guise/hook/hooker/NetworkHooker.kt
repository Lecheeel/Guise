package com.houvven.guise.hook.hooker

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime

internal class NetworkHooker(private val runtime: HookRuntime, private val profile: HookProfiles) {
    @Suppress("DEPRECATION")
    fun install() {
        profile.mobileNetType?.let { type ->
            listOf("getNetworkType", "getDataNetworkType").forEach { name -> runtime.replace("android.telephony.TelephonyManager", name) { type } }
        }
        profile.simOperator?.let { operator ->
            listOf("getSimOperator", "getNetworkOperator").forEach { name -> runtime.replace("android.telephony.TelephonyManager", name) { operator } }
        }
        profile.networkType?.let { transport ->
            if (transport == -1) {
                listOf("getActiveNetwork", "getActiveNetworkInfo", "getNetworkInfo").forEach { name ->
                    runtime.replace("android.net.ConnectivityManager", name) { null }
                }
                runtime.replace("android.net.ConnectivityManager", "isDefaultNetworkActive") { false }
                return@let
            }
            val legacy = when (transport) {
                NetworkCapabilities.TRANSPORT_WIFI -> ConnectivityManager.TYPE_WIFI
                NetworkCapabilities.TRANSPORT_CELLULAR -> ConnectivityManager.TYPE_MOBILE
                NetworkCapabilities.TRANSPORT_ETHERNET, 8 /* TRANSPORT_USB (API 31+) */ -> ConnectivityManager.TYPE_ETHERNET
                NetworkCapabilities.TRANSPORT_BLUETOOTH -> ConnectivityManager.TYPE_BLUETOOTH
                NetworkCapabilities.TRANSPORT_VPN -> ConnectivityManager.TYPE_VPN
                else -> return@let
            }
            runtime.replace("android.net.NetworkInfo", "getType") { legacy }
            runtime.replace("android.net.NetworkInfo", "getTypeName") { when (legacy) {
                ConnectivityManager.TYPE_WIFI -> "WIFI"
                ConnectivityManager.TYPE_MOBILE -> "MOBILE"
                ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
                ConnectivityManager.TYPE_BLUETOOTH -> "BLUETOOTH"
                else -> "VPN"
            } }
            runtime.replace("android.net.NetworkCapabilities", "hasTransport") { chain -> chain.args[0] == transport }
            runtime.replace("android.net.NetworkCapabilities", "getTransportTypes") { intArrayOf(transport) }
            // Network.netId identifies an actual network; changing it breaks networking.
        }
    }
}
