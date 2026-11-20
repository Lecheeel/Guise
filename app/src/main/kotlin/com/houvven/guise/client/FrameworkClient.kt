package com.houvven.guise.client

import android.util.Log
import com.houvven.guise.R
import com.houvven.guise.hook.ModuleStatus
import com.houvven.guise.hook.profile.HookProfiles
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Public framework API; scope approval belongs to the framework's own UI. */
object FrameworkClient {
    private val messages = Channel<Int>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun setScope(profiles: HookProfiles) {
        val packageName = requireNotNull(profiles.packageName)
        val service = ModuleStatus.service
        if (service == null) {
            messages.trySend(R.string.framework_unavailable)
            return
        }
        runCatching {
            if (!profiles.isAvailable) {
                service.removeScope(listOf(packageName))
            } else if (packageName !in service.scope) {
                service.requestScope(listOf(packageName), object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) {
                        messages.trySend(if (packageName in approved) R.string.scope_approved else R.string.scope_failed)
                    }

                    override fun onScopeRequestFailed(message: String) {
                        Log.w("GuiseService", "Scope request failed: $message")
                        messages.trySend(R.string.scope_failed)
                    }
                })
            }
        }.onFailure {
            Log.e("GuiseService", "Could not update module scope", it)
            messages.trySend(R.string.scope_failed)
        }
    }
}
