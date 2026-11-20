package com.houvven.guise.hook.hooker

import android.os.Build
import com.houvven.guise.hook.profile.item.PropertiesProfile
import com.houvven.guise.hook.util.HookRuntime

internal class PropertiesHooker(private val runtime: HookRuntime, private val profile: PropertiesProfile) {
    fun install() {
        val values = listOf(
            Triple("BRAND", "ro.product.brand", profile.brand),
            Triple("MANUFACTURER", "ro.product.manufacturer", profile.manufacturer),
            Triple("MODEL", "ro.product.model", profile.model),
            Triple("PRODUCT", "ro.product.name", profile.product),
            Triple("DEVICE", "ro.product.device", profile.device),
            Triple("DISPLAY", "ro.build.display.id", profile.displayId),
            Triple("FINGERPRINT", "ro.build.fingerprint", profile.fingerprint),
        )
        values.forEach { (field, _, value) ->
            if (value != null) runtime.setField(null, Build::class.java, field, value)
        }
        val properties = buildMap {
            values.forEach { (_, key, value) -> if (value != null) put(key, value) }
            profile.characteristics?.let { put("ro.build.characteristics", it) }
            putAll(profile.customProperties)
        }
        if (properties.isEmpty()) return
        runtime.after("android.os.SystemProperties", "get", "getInt", "getLong", "getBoolean") { original, chain ->
            val value = properties[chain.args.firstOrNull()] ?: return@after original
            when (chain.executable.name) {
                "getInt" -> value.toIntOrNull() ?: original
                "getLong" -> value.toLongOrNull() ?: original
                "getBoolean" -> when (value.lowercase()) {
                    "1", "true", "y", "yes", "on" -> true
                    "0", "false", "n", "no", "off" -> false
                    else -> original
                }
                else -> value
            }
        }
    }
}
