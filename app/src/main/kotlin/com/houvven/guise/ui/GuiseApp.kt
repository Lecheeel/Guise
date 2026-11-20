package com.houvven.guise.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.houvven.guise.client.FrameworkClient
import androidx.compose.ui.platform.LocalResources
import com.houvven.guise.ui.theme.AppTheme
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs


val LocalNavHostController: ProvidableCompositionLocal<NavHostController> =
    staticCompositionLocalOf { error("Not provided") }

val LocalSnackBarHostState: ProvidableCompositionLocal<SnackbarHostState> =
    staticCompositionLocalOf { error("Not provided") }

@Composable
fun GuiseApp(
    navController: NavHostController = rememberNavController(),
    hostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    AppTheme {
        CompositionLocalProvider(
            LocalNavHostController provides navController,
            LocalSnackBarHostState provides hostState
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0),
                snackbarHost = {
                    SnackbarHost(hostState = LocalSnackBarHostState.current) {
                        Snackbar(snackbarData = it)
                    }
                }
            ) { innerPadding ->
                DestinationsNavHost(
                    modifier = Modifier.padding(innerPadding),
                    navController = LocalNavHostController.current,
                    navGraph = NavGraphs.root
                )

                val resources = LocalResources.current
                LaunchedEffect(hostState, resources) {
                    FrameworkClient.events.collect { message -> hostState.showSnackbar(resources.getString(message)) }
                }
            }
        }
    }
}
