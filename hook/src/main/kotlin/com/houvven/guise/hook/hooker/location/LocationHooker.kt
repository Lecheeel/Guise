package com.houvven.guise.hook.hooker.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.util.HookRuntime
import java.util.function.Consumer

internal class LocationHooker(private val runtime: HookRuntime, private val profile: HookProfiles) {
    fun install() {
        if (!profile.isLocationAvailable) return
        GnssStatusHooker(runtime).install()
        runtime.after("android.location.LocationManager", "getLastKnownLocation", "getLastLocation") { original, _ ->
            provideLocation(original as? Location)
        }
        listOf("isLocationEnabledForUser", "isLocationEnabled", "isProviderEnabledForUser", "isProviderEnabled").forEach {
            runtime.replace("android.location.LocationManager", it) { true }
        }
        runtime.replace("android.location.LocationManager", "getBestProvider") { LocationManager.GPS_PROVIDER }
        runtime.after("android.location.LocationManager", "getProviders", "getAllProviders") { original, _ ->
            (original as? List<*>)?.filterIsInstance<String>()?.plus(LocationManager.GPS_PROVIDER)?.distinct() ?: original
        }
        runtime.methods("android.location.LocationManager", "requestSingleUpdate", "requestLocationUpdates", "getCurrentLocation").forEach { method ->
            runtime.before(method) { chain ->
                chain.args.toTypedArray().also { args ->
                    args.forEach { argument ->
                        when (argument) {
                            is LocationListener -> hookCallbacks(argument.javaClass)
                            is Consumer<*> -> hookCallbacks(argument.javaClass)
                        }
                    }
                }
            }
        }
    }

    private fun hookCallbacks(type: Class<*>) {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            current.declaredMethods.filter { method ->
                method.name in setOf("onLocationChanged", "accept") && !java.lang.reflect.Modifier.isAbstract(method.modifiers)
            }.forEach { method ->
                runtime.before(method) { chain ->
                    chain.args.map { arg -> when (arg) {
                        is Location -> provideLocation(arg)
                        is List<*> -> if (arg.all { it is Location }) arg.map { provideLocation(it as Location) } else arg
                        else -> arg
                    } }.toTypedArray()
                }
            }
            current = current.superclass
        }
    }

    private fun provideLocation(original: Location?): Location =
        (original?.let(::Location) ?: Location(LocationManager.GPS_PROVIDER)).apply {
            longitude = profile.longitude ?: longitude
            latitude = profile.latitude ?: latitude
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (!hasAccuracy()) accuracy = 5f
        }
}
