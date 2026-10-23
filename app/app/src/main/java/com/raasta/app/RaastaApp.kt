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
import com.raasta.app.ui.feedback.FeedbackScreen
import com.raasta.app.ui.feedback.ReportsScreen
import com.raasta.app.ui.home.HomeScreen
import com.raasta.app.ui.login.LoginScreen
import com.raasta.app.ui.plan.CompareRoutesScreen
import com.raasta.app.ui.plan.JourneyDetailsScreen
import com.raasta.app.ui.plan.JourneyScreen
import com.raasta.app.ui.plan.RouteCheckScreen
import com.raasta.app.ui.prefs.PreferencesScreen
import com.raasta.app.ui.search.SearchScreen
import kotlinx.coroutines.launch

/** Journey input -> preferences -> route analysis -> comparison -> details -> journey -> feedback. */
private object Routes {
    const val HOME = "home"
    const val SEARCH = "search/{field}"
    const val PREFERENCES = "preferences"
    const val CHECK = "check"
    const val COMPARE = "compare"
    const val DETAILS = "details"
    const val JOURNEY = "journey"
    const val FEEDBACK = "feedback"
    const val REPORTS = "reports"

    fun search(editFrom: Boolean) = "search/${if (editFrom) "from" else "to"}"
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
                onEditFrom = { navController.navigate(Routes.search(editFrom = true)) },
                onEditTo = { navController.navigate(Routes.search(editFrom = false)) },
                onContinue = { navController.navigate(Routes.PREFERENCES) },
                onReports = { navController.navigate(Routes.REPORTS) },
                onSignOut = onSignOut,
            )
        }
        composable(Routes.SEARCH) { entry ->
            SearchScreen(
                vm = vm,
                editFrom = entry.arguments?.getString("field") == "from",
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.PREFERENCES) {
            PreferencesScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onFindRoutes = { navController.navigate(Routes.CHECK) },
            )
        }
        composable(Routes.CHECK) {
            RouteCheckScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onCompare = { navController.navigate(Routes.COMPARE) { popUpTo(Routes.PREFERENCES) } },
            )
        }
        composable(Routes.COMPARE) {
            CompareRoutesScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onEditPreferences = { navController.popBackStack(Routes.PREFERENCES, inclusive = false) },
                onDetails = { navController.navigate(Routes.DETAILS) },
            )
        }
        composable(Routes.DETAILS) {
            JourneyDetailsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onStart = { journey ->
                    vm.startTrip(journey)
                    navController.navigate(Routes.JOURNEY) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(Routes.JOURNEY) {
            JourneyScreen(
                vm = vm,
                onArrived = { navController.navigate(Routes.FEEDBACK) { popUpTo(Routes.HOME) } },
                onEnded = { navController.backToHome() },
            )
        }
        composable(Routes.FEEDBACK) {
            FeedbackScreen(
                vm = vm,
                onPlanAnother = { navController.backToHome() },
                onReports = { navController.navigate(Routes.REPORTS) },
            )
        }
        composable(Routes.REPORTS) {
            ReportsScreen(vm = vm, onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.backToHome() {
    popBackStack(Routes.HOME, inclusive = false)
}
