package com.houvven.guise.hook.hooker

import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime

internal class WifiHooker(private val runtime: HookRuntime, private val profiles: HookProfiles) {
    fun install() {
        val ssid = if (profiles.disableWifiLocation) "<unknown ssid>" else profiles.wifiSsid
        val bssid = if (profiles.disableWifiLocation) "02:00:00:00:00:00" else profiles.wifiBssid
        val mac = if (profiles.disableWifiLocation) "02:00:00:00:00:00" else profiles.wifiMac
        ssid?.let { value -> runtime.replace("android.net.wifi.WifiInfo", "getSSID") { if (value == "<unknown ssid>") value else "\"$value\"" } }
        bssid?.let { value -> runtime.replace("android.net.wifi.WifiInfo", "getBSSID") { value } }
        mac?.let { value -> runtime.replace("android.net.wifi.WifiInfo", "getMacAddress") { value } }
        if (profiles.networkType == NetworkCapabilities.TRANSPORT_WIFI) {
            runtime.replace("android.net.wifi.WifiManager", "getWifiState") { WifiManager.WIFI_STATE_ENABLED }
        }
    }
}
