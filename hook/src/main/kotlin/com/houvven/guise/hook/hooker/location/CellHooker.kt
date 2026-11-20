package com.houvven.guise.hook.hooker.location

import android.telephony.gsm.GsmCellLocation
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime

internal class CellHooker(private val runtime: HookRuntime, private val profile: HookProfiles) {
    @Suppress("DEPRECATION")
    fun install() {
        val cid = if (profile.disableCellLocation) 0L else profile.cid
        val lac = if (profile.disableCellLocation) 0 else profile.lac
        val pci = if (profile.disableCellLocation) 0 else profile.pci
        val types = listOf("Gsm", "Wcdma", "Tdscdma", "Lte", "Nr")
        types.forEach { kind ->
            val type = "android.telephony.CellIdentity$kind"
            cid?.let { value ->
                val getter = when (kind) { "Nr" -> "getNci"; "Lte" -> "getCi"; else -> "getCid" }
                if (kind == "Nr") runtime.replace(type, getter) { value }
                else runtime.replace(type, getter) { value.toInt() }
            }
            lac?.let { value -> runtime.replace(type, if (kind in listOf("Nr", "Lte")) "getTac" else "getLac") { value } }
            if (kind in listOf("Nr", "Lte")) pci?.let { value -> runtime.replace(type, "getPci") { value } }
            profile.mcc?.let { value -> runtime.replace(type, "getMccString") { value }; value.toIntOrNull()?.let { n -> runtime.replace(type, "getMcc") { n } } }
            profile.mnc?.let { value -> runtime.replace(type, "getMncString") { value }; value.toIntOrNull()?.let { n -> runtime.replace(type, "getMnc") { n } } }
        }
        runtime.after("android.telephony.TelephonyManager", "getCellLocation") { original, _ ->
            (original as? GsmCellLocation)?.apply { setLacAndCid(lac ?: this.lac, cid?.toInt() ?: this.cid) } ?: original
        }
    }
}
