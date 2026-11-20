package com.houvven.guise.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.util.Log
import com.houvven.guise.hook.store.impl.SharedPreferenceModuleStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-side status comes from the official service, never from a self-hook. */
object ModuleStatus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableService = MutableStateFlow<XposedService?>(null)
    val serviceState = mutableService.asStateFlow()
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    val isModuleActive get() = mutableActive.value
    val service get() = mutableService.value

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        fun sync(service: XposedService) {
            if (!appContext.getSystemService(UserManager::class.java).isUserUnlocked) return
            scope.launch {
                runCatching { SharedPreferenceModuleStore.Hooker(appContext).syncTo(service) }
                    .onFailure { Log.e("GuiseService", "Could not synchronize profiles", it) }
            }
        }
        // XposedProvider may start the application during Direct Boot.
        appContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                service?.let(::sync)
            }
        }, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                val supported = runCatching {
                    service.apiVersion >= 102 && service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L
                }.getOrDefault(false)
                mutableService.value = if (supported) service else null
                mutableActive.value = supported
                if (supported) sync(service)
            }
            override fun onServiceDied(service: XposedService) {
                if (mutableService.value === service) {
                    mutableActive.value = false
                    mutableService.value = null
                }
            }
        })
    }
}
