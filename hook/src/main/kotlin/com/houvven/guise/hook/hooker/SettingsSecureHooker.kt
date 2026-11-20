package com.houvven.guise.hook.hooker

import android.provider.Settings.Secure
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime

internal class SettingsSecureHooker(private val runtime: HookRuntime, private val profile: HookProfiles) {
    @Suppress("DEPRECATION")
    fun install() {
        if (profile.ssaid.isNullOrBlank() && !profile.isLocationAvailable) return
        runtime.after("android.provider.Settings\$Secure", "getString", "getStringForUser") { original, chain ->
            when (chain.args.getOrNull(1)) {
                Secure.ANDROID_ID -> profile.ssaid?.takeIf { it.isNotBlank() } ?: original
                Secure.LOCATION_MODE -> if (profile.isLocationAvailable) Secure.LOCATION_MODE_HIGH_ACCURACY.toString() else original
                else -> original
            }
        }
    }
}
