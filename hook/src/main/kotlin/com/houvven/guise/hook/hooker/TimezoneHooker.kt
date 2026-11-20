package com.houvven.guise.hook.hooker

import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime
import java.util.TimeZone

internal class TimezoneHooker(private val runtime: HookRuntime, private val profile: HookProfiles) {
    fun install() {
        profile.timezone?.let { id ->
            runtime.replace("java.util.TimeZone", "getDefault") { TimeZone.getTimeZone(id) }
            runtime.replace("java.util.TimeZone", "getDefaultRef") { TimeZone.getTimeZone(id) }
        }
    }
}
