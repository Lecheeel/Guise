package com.houvven.guise.hook

import android.util.Log
import com.houvven.guise.hook.hooker.*
import com.houvven.guise.hook.hooker.location.*
import com.houvven.guise.hook.store.impl.SharedPreferenceModuleStore
import com.houvven.guise.hook.util.HookRuntime
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class HookEntry : XposedModule() {
    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage || param.packageName in setOf("android", "com.android.phone", "com.houvven.guise")) return
        val profiles = runCatching {
            SharedPreferenceModuleStore.Hooked(getRemotePreferences(SharedPreferenceModuleStore.NAME)).get(param.packageName)
        }.getOrElse {
            log(Log.ERROR, "GuiseHook", "Cannot load profiles for ${param.packageName}", it)
            return
        }
        if (!profiles.isAvailable) return
        val runtime = HookRuntime(this, param.classLoader, param.packageName)
        listOf<() -> Unit>(
            { PropertiesHooker(runtime, profiles.properties).install() },
            { PackageHooker(runtime, profiles).install() },
            { ResourceConfigurationHooker(runtime, profiles).install() },
            { LocationHooker(runtime, profiles).install() },
            { CellHooker(runtime, profiles).install() },
            { SettingsSecureHooker(runtime, profiles).install() },
            { TimezoneHooker(runtime, profiles).install() },
            { NetworkHooker(runtime, profiles).install() },
            { WifiHooker(runtime, profiles).install() },
        ).forEach { install ->
            runCatching(install).onFailure { runtime.warn("Hook installation failed", it) }
        }
    }
}
