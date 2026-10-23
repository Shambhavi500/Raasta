package com.raasta.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raasta.app.auth.DemoSessionStore
import com.raasta.app.auth.SessionState
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.home.HomeScreen
import com.raasta.app.ui.login.LoginScreen
import com.raasta.app.ui.plan.ArrivedScreen
import com.raasta.app.ui.plan.RecoverScreen
import com.raasta.app.ui.plan.RouteOptionsScreen
import com.raasta.app.ui.plan.TripScreen
import com.raasta.app.ui.search.SearchScreen
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val OPTIONS = "options"
    const val TRIP = "trip"
    const val ARRIVED = "arrived"
    const val RECOVER = "recover"
}

@Composable
fun RaastaApp(sessionStore: DemoSessionStore) {
    val session by sessionStore.session.collectAsStateWithLifecycle(initialValue = SessionState.Loading)
    val scope = rememberCoroutineScope()

    when (val state = session) {
        SessionState.Loading ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        SessionState.SignedOut ->
            LoginScreen(onSignedIn = { phone -> scope.launch { sessionStore.signIn(phone) } })
        is SessionState.SignedIn ->
            SignedInNavHost(
                phone = state.phone,
                onSignOut = { scope.launch { sessionStore.signOut() } },
            )
    }
}

@Composable
private fun SignedInNavHost(phone: String, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val vm: TripViewModel = viewModel()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                phone = phone,
                onSearch = {
                    vm.startPlanning()
                    navController.navigate(Routes.SEARCH)
                },
                onDestination = { id ->
                    vm.startPlanning()
                    vm.chooseDestination(id)
                    navController.navigate(Routes.OPTIONS)
                },
                onBusDidNotCome = { navController.navigate(Routes.RECOVER) },
                onSignOut = onSignOut,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.OPTIONS) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(Routes.RECOVER) {
            RecoverScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onFindOptions = { navController.navigate(Routes.OPTIONS) },
            )
        }
        composable(Routes.OPTIONS) {
            RouteOptionsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onStart = { journey ->
                    vm.startTrip(journey)
                    navController.navigate(Routes.TRIP) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(Routes.TRIP) {
            TripScreen(
                vm = vm,
                onArrived = { navController.navigate(Routes.ARRIVED) { popUpTo(Routes.HOME) } },
                onEnded = { navController.backToHome() },
            )
        }
        composable(Routes.ARRIVED) {
            ArrivedScreen(vm = vm, onDone = { navController.backToHome() })
        }
    }
}

private fun NavHostController.backToHome() {
    popBackStack(Routes.HOME, inclusive = false)
}
