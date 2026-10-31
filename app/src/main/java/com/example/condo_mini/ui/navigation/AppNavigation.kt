package com.example.condo_mini.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.condo_mini.ui.screens.login.LoginScreen
import com.example.condo_mini.ui.screens.login.LoginViewModel
import com.example.condo_mini.ui.screens.main.MainScreen
import com.example.condo_mini.ui.screens.units.UnitChoiceScreen

object AppRoutes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val CHOOSE_UNIT = "choose_unit"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    loginViewModel: LoginViewModel = viewModel()
) {
    val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.LOGIN,
        modifier = modifier
    ) {
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                uiState = uiState,
                onUserNameChanged = { loginViewModel.onUserNameChanged(it) },
                onUnitNumberChanged = { loginViewModel.onUnitNumberChanged(it) },
                onRoleChanged = { loginViewModel.onRoleChanged(it) },
                onLoginClick = {
                    if (loginViewModel.login()) {
                        val next = if (loginViewModel.needsUnitChoice()) AppRoutes.CHOOSE_UNIT else AppRoutes.MAIN
                        navController.navigate(next) {
                            popUpTo(AppRoutes.LOGIN) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(AppRoutes.CHOOSE_UNIT) {
            UnitChoiceScreen(
                onChosen = {
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.CHOOSE_UNIT) { inclusive = true }
                    }
                }
            )
        }

        // As abas mudam conforme o perfil do usuário logado (morador ou síndico).
        composable(AppRoutes.MAIN) {
            MainScreen(
                onLogoutClick = {
                    loginViewModel.logout()
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.MAIN) { inclusive = true }
                    }
                }
            )
        }
    }
}
