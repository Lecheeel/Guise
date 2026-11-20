package com.houvven.guise.hook.hooker

import android.content.res.Configuration
import android.os.LocaleList
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime
import java.util.Locale

internal class ResourceConfigurationHooker(private val runtime: HookRuntime, private val profile: HookProfiles) {
    fun install() {
        if (profile.language == null && profile.nightMode == null && profile.densityDpi == null && profile.fontScale == null) return
        runtime.after("android.content.res.Resources", "getConfiguration") { original, _ ->
            val configuration = original as? Configuration ?: return@after original
            Configuration(configuration).apply {
                profile.language?.let { setLocales(LocaleList(Locale.forLanguageTag(it.replace('_', '-')))) }
                profile.nightMode?.let {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (it) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                profile.densityDpi?.let { densityDpi = it }
                profile.fontScale?.let { fontScale = it }
            }
        }
    }
}
