package com.raasta.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raasta.app.auth.DemoSessionStore
import com.raasta.app.auth.SessionState
import com.raasta.app.planner.Corridor
import com.raasta.app.ui.home.HomeScreen
import com.raasta.app.ui.login.LoginScreen
import com.raasta.app.ui.plan.PlanTripScreen
import com.raasta.app.ui.plan.RecoveryScreen
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val PLAN = "plan"
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
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                phone = phone,
                onPlanTrip = { navController.navigate(Routes.PLAN) },
                onConnectionFailed = { navController.navigate(Routes.RECOVER) },
                onSignOut = onSignOut,
            )
        }
        composable(Routes.PLAN) {
            val context = LocalContext.current
            val corridor = remember { Corridor.load(context) }
            PlanTripScreen(corridor = corridor, onBack = { navController.popBackStack() })
        }
        composable(Routes.RECOVER) {
            val context = LocalContext.current
            val corridor = remember { Corridor.load(context) }
            RecoveryScreen(corridor = corridor, onBack = { navController.popBackStack() })
        }
    }
}
