package com.zoomers.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zoomers.app.feature.auth.LoginScreen
import com.zoomers.app.feature.auth.RegisterScreen

/**
 * Root of the app. Gates between the authentication flow and the main experience
 * based on the persisted session, and threads the [WindowSizeClass] down so every
 * screen can adapt to phones, foldables and tablets.
 */
@Composable
fun ZoomersApp(windowSizeClass: WindowSizeClass, rootViewModel: RootViewModel = hiltViewModel()) {
    val authState by rootViewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        AuthState.Loading -> Box(Modifier.fillMaxSize()) // brief splash; window background shows through
        AuthState.LoggedOut -> AuthNavHost()
        AuthState.LoggedIn -> MainScaffold(windowSizeClass = windowSizeClass)
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(onNavigateToRegister = { navController.navigate(Routes.REGISTER) })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { navController.popBackStack() })
        }
    }
}
