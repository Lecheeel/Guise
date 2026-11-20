package com.houvven.guise.hook.hooker.location

import com.houvven.guise.hook.util.HookRuntime
import java.lang.reflect.Method

internal class GnssStatusHooker(private val runtime: HookRuntime) {
    fun install() {
        runtime.replace("android.location.GnssStatus", "getSatelliteCount") { 0 }
        listOf("addNmeaListener", "addGpsStatusListener", "registerGnssStatusCallback",
            "registerGnssMeasurementsCallback", "registerGnssNavigationMessageCallback", "registerAntennaInfoListener"
        ).forEach { name ->
            runtime.replace("android.location.LocationManager", name) { chain ->
                if ((chain.executable as Method).returnType == Boolean::class.javaPrimitiveType) true else null
            }
        }
    }
}
