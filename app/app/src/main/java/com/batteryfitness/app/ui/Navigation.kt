package com.batteryfitness.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.batteryfitness.app.BuildConfig
import com.batteryfitness.app.domain.BatteryTest
import com.batteryfitness.app.ui.screens.RegisterAthleteScreen
import com.batteryfitness.app.ui.screens.SessionScreen
import com.batteryfitness.app.ui.screens.SessionSetupScreen
import com.batteryfitness.app.ui.screens.label

private object Route {
    const val HOME = "home"
    const val REGISTER = "register"
    const val SETUP = "setup"
    const val SESSION = "session/{id}"
    const val CELL = "cell/{athleteId}/{testKey}"
    fun session(id: Long) = "session/$id"
    fun cell(athleteId: Long, testKey: String) = "cell/$athleteId/$testKey"
}

@Composable
fun BatteryFitnessApp() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()

    NavHost(navController = nav, startDestination = Route.HOME) {

        composable(Route.HOME) {
            HomeScreen(
                onStartSession = { nav.navigate(Route.SETUP) },
                onRegister = { nav.navigate(Route.REGISTER) },
            )
        }

        composable(Route.REGISTER) {
            RegisterAthleteScreen(
                vm = vm,
                onRegistered = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Route.SETUP) {
            SessionSetupScreen(
                vm = vm,
                onStarted = { id ->
                    nav.navigate(Route.session(id)) {
                        popUpTo(Route.HOME)
                    }
                },
                onRegisterAthlete = { nav.navigate(Route.REGISTER) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Route.SESSION,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            androidx.compose.runtime.LaunchedEffect(id) { vm.loadSession(id) }
            SessionScreen(
                vm = vm,
                onOpenCell = { athleteId, test ->
                    nav.navigate(Route.cell(athleteId, test.key))
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Route.CELL,
            arguments = listOf(
                navArgument("athleteId") { type = NavType.LongType },
                navArgument("testKey") { type = NavType.StringType },
            ),
        ) { entry ->
            val athleteId = entry.arguments?.getLong("athleteId") ?: 0L
            val test = BatteryTest.fromKey(entry.arguments?.getString("testKey").orEmpty())
            TestCellPlaceholder(
                athleteId = athleteId,
                test = test,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onStartSession: () -> Unit, onRegister: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Battery Fitness Assessment") }) },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "10-test SAI battery, measured on this device.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Works with no internet connection. Video and AI analysis never " +
                    "leave the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )

            Button(onClick = onStartSession, modifier = Modifier.fillMaxWidth()) {
                Text("Start assessment session")
            }
            Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
                Text("Register an athlete")
            }

            // Submission §2.2 requires version, build date and team name to be
            // visible in the app.
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("About", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text("Team: ${BuildConfig.TEAM_NAME}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Version: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Build date: ${BuildConfig.BUILD_DATE}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Placeholder for the per-test capture and measurement screen.
 *
 * Phase 3 replaces this with protocol guidance, the capture-guidance wizard,
 * CameraX recording with the §6.3.1 duration gate, and the measurement result
 * with its confidence band.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestCellPlaceholder(
    athleteId: Long,
    test: BatteryTest?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(test?.label() ?: "Test") }) },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Athlete #$athleteId", style = MaterialTheme.typography.titleMedium)
            test?.let {
                Text(
                    "Attempts: ${it.attempts} · Unit: ${it.unit}\n" +
                        if (it.isPhoto) "Capture: single photo"
                        else "Capture: ${it.minDurationSec}–${it.maxDurationSec}s video",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (it.measurement.name == "AI") "Measured by on-device AI"
                    else "Manual entry with protocol guidance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Capture pipeline lands in the next build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}
