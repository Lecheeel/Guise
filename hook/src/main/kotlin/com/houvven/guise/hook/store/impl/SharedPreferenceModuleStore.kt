package com.houvven.guise.hook.store.impl

import android.content.Context
import android.content.SharedPreferences
import com.houvven.guise.hook.ModuleStatus
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.store.ModuleStore
import io.github.libxposed.service.XposedService

object SharedPreferenceModuleStore {
    const val NAME = "guise.hook.profiles"
    private val lock = Any()

    /** Keep the original private preferences as a durable local copy and migration source. */
    class Hooker(context: Context) : ModuleStore.Hooker() {
        private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

        override val configuredPackages: Set<String>
            get() = prefs.all.filterValues { it is String }.keys

        override fun set(profiles: HookProfiles) = synchronized(lock) {
            val packageName = requireNotNull(profiles.packageName)
            val editor = prefs.edit()
            if (profiles.isAvailable) editor.putString(packageName, profiles.toJsonStr())
            else editor.remove(packageName)
            check(editor.commit()) { "Could not save profiles" }
            ModuleStatus.service?.let { syncTo(it) }
            Unit
        }

        fun syncTo(service: XposedService) = synchronized(lock) {
            val editor = service.getRemotePreferences(NAME).edit().clear()
            prefs.all.forEach { (key, value) -> if (value is String) editor.putString(key, value) }
            check(editor.commit()) { "Could not synchronize profiles to Xposed" }
        }

        override fun get(packageName: String) = decode(prefs, packageName)
        override fun isEnabled(packageName: String) = prefs.contains(packageName)
    }

    class Hooked(private val prefs: SharedPreferences) : ModuleStore.Hooked() {
        override fun get(packageName: String) = decode(prefs, packageName)
        override fun isEnabled(packageName: String) = prefs.contains(packageName)
    }

    private fun decode(prefs: SharedPreferences, packageName: String): HookProfiles =
        HookProfiles.fromJsonStr(prefs.getString(packageName, null).orEmpty()).copy(packageName = packageName)
}
