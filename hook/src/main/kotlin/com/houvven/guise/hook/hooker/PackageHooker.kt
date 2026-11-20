package com.houvven.guise.hook.hooker

import android.content.pm.PackageInfo
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime

internal class PackageHooker(private val runtime: HookRuntime, private val profile: HookProfiles) {
    @Suppress("DEPRECATION")
    fun install() {
        if (profile.versionName == null && profile.versionCode == null) return
        runtime.after("android.app.ApplicationPackageManager", "getPackageInfo") { original, _ ->
            (original as? PackageInfo)?.apply {
                if (packageName == runtime.packageName) {
                    profile.versionName?.let { versionName = it }
                    profile.versionCode?.let { versionCode = it; if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode = it.toLong() }
                }
            } ?: original
        }
        runtime.findClass("${runtime.packageName}.BuildConfig")?.let { type ->
            profile.versionName?.let { runtime.setField(null, type, "VERSION_NAME", it) }
            profile.versionCode?.let { runtime.setField(null, type, "VERSION_CODE", it) }
        }
    }
}
