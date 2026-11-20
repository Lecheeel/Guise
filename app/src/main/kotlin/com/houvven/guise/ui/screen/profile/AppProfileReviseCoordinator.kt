package com.houvven.guise.ui.screen.profile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.highcapable.betterandroid.system.extension.component.clipboardManager
import com.highcapable.betterandroid.system.extension.component.copy
import com.houvven.guise.R
import com.houvven.guise.client.FrameworkClient
import com.houvven.guise.hook.profile.HookProfiles
import com.houvven.guise.hook.store.ModuleStore
import com.houvven.guise.ui.screen.profile.components.ProfileReviseState
import com.houvven.guise.util.app.App
import com.houvven.guise.util.app.AppUtils
import com.houvven.guise.util.showToast
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen's coordinator which is responsible for handling actions from the UI layer
 * and one-shot actions based on the new UI state
 */
class AppProfileReviseCoordinator(
    val viewModel: AppProfileReviseViewModel,
    val reviseState: ProfileReviseState,
    val moduleStore: ModuleStore.Hooker,
    val app: App,
    val context: Context,
    private val scope: CoroutineScope,
) {
    val screenStateFlow = viewModel.stateFlow

    fun onSave() {
        val profiles = reviseState.profilesState.value
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    moduleStore.set(profiles)
                    FrameworkClient.setScope(profiles)
                }
            }
            context.showToast(if (result.isSuccess) R.string.profile_saved else R.string.profile_save_failed)
        }
    }

    fun onClearAll() {
        reviseState.update(HookProfiles.Empty.copy(packageName = app.packageName))
    }

    fun onRestart() {
        runCatching {
            require(AppUtils.cmdStopApp(app.packageName))
            AppUtils.launchApp(context, app.packageName)
        }.onFailure {
            context.showToast(R.string.required_permissions_root)
        }
    }

    fun onStop() {
        if (AppUtils.cmdStopApp(app.packageName)) {
            context.showToast(R.string.success)
            return
        }
        AppUtils.toDetails(context, app.packageName)
    }

    fun onClearData() {
        val result = AppUtils.cmdClearData(app.packageName)
        val id = if (result) R.string.success else R.string.required_permissions_root
        context.showToast(id)
    }

    fun onCopyToClipboard() {
        val profiles by reviseState.profilesState
        val clipboardManager = context.clipboardManager
        clipboardManager.copy(profiles.toJsonStr())
        context.showToast(R.string.success)
    }
}

@Composable
fun rememberAppProfileReviseCoordinator(
    app: App,
    reviseState: ProfileReviseState,
    moduleStore: ModuleStore.Hooker,
    context: Context = LocalContext.current,
    viewModel: AppProfileReviseViewModel = koinViewModel()
): AppProfileReviseCoordinator {
    val scope = rememberCoroutineScope()
    return remember(viewModel, app, reviseState, moduleStore, context, scope) {
        AppProfileReviseCoordinator(
            viewModel = viewModel,
            reviseState = reviseState,
            moduleStore = moduleStore,
            app = app,
            context = context,
            scope = scope,
        )
    }
}
