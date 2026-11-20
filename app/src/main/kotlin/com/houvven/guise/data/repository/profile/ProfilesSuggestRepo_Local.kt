package com.houvven.guise.data.repository.profile

import android.content.Context
import android.net.NetworkCapabilities
import android.os.Build
import com.houvven.guise.R
import com.houvven.guise.data.domain.ProfileSuggest

private typealias LocalRepo<T> = ProfilesSuggestRepo.Local<T>

object MobileNetworkTypeRepo : LocalRepo<Int> {
    // Public Android network-type values; no hidden API exemption is needed.
    override fun get(): List<ProfileSuggest<out Int>> = listOf(
        "UNKNOWN", "GPRS", "EDGE", "UMTS", "CDMA", "EVDO_0", "EVDO_A", "1xRTT",
        "HSDPA", "HSUPA", "HSPA", "IDEN", "EVDO_B", "LTE", "EHRPD", "HSPAP",
        "GSM", "TD_SCDMA", "IWLAN", "LTE_CA", "NR"
    ).mapIndexed { value, label -> ProfileSuggest(label, value) }
}

class NetworkType(context: Context) : LocalRepo<Int> {

    private val networkTypes by lazy {
        ProfileSuggest.create(
            context,
            R.string.network_type_none to -1,
            R.string.network_type_cellular to NetworkCapabilities.TRANSPORT_CELLULAR,
            R.string.network_type_wifi to NetworkCapabilities.TRANSPORT_WIFI,
            R.string.network_type_bluetooth to NetworkCapabilities.TRANSPORT_BLUETOOTH,
            R.string.network_type_vpn to NetworkCapabilities.TRANSPORT_VPN,
            R.string.network_type_ethernet to NetworkCapabilities.TRANSPORT_ETHERNET
        ).toMutableList().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(ProfileSuggest("USB", NetworkCapabilities.TRANSPORT_USB))
            }
        }
    }

    override fun get(): List<ProfileSuggest<out Int>> {
        return networkTypes
    }
}