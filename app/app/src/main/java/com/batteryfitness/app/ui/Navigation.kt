package com.batteryfitness.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.batteryfitness.app.domain.BatteryTest
import com.batteryfitness.app.domain.Measurement
import com.batteryfitness.app.ui.screens.CaptureScreen
import com.batteryfitness.app.ui.screens.HomeScreen
import com.batteryfitness.app.ui.screens.RegisterAthleteScreen
import com.batteryfitness.app.ui.screens.SessionScreen
import com.batteryfitness.app.ui.screens.SessionSetupScreen
import com.batteryfitness.app.ui.screens.label
import com.batteryfitness.app.ui.theme.AppBackground

private object Route {
    const val HOME = "home"
    const val REGISTER = "register"
    const val SETUP = "setup"
    const val SESSION = "session/{id}"
    const val CELL = "cell/{athleteId}/{testKey}"
    const val CAPTURE = "capture/{athleteId}/{testKey}/{sessionId}"
    fun session(id: Long) = "session/$id"
    fun cell(athleteId: Long, testKey: String) = "cell/$athleteId/$testKey"
    fun capture(athleteId: Long, testKey: String, sessionId: Long) =
        "capture/$athleteId/$testKey/$sessionId"
}

@Composable
fun BatteryFitnessApp(languages: LanguageController) {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()

    NavHost(navController = nav, startDestination = Route.HOME) {

        composable(Route.HOME) {
            HomeScreen(
                languages = languages,
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
                onStarted = { id -> nav.navigate(Route.session(id)) { popUpTo(Route.HOME) } },
                onRegisterAthlete = { nav.navigate(Route.REGISTER) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Route.SESSION,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            LaunchedEffect(id) { vm.loadSession(id) }
            SessionScreen(
                vm = vm,
                onOpenCell = { athleteId, test ->
                    nav.navigate(Route.cell(athleteId, test.key))
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Route.CAPTURE,
            arguments = listOf(
                navArgument("athleteId") { type = NavType.LongType },
                navArgument("testKey") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.LongType },
            ),
        ) { entry ->
            val test = BatteryTest.fromKey(entry.arguments?.getString("testKey").orEmpty())
            if (test == null) { nav.popBackStack() } else {
                CaptureScreen(
                    test = test,
                    athleteId = entry.arguments?.getLong("athleteId") ?: 0L,
                    sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                    onCaptured = { _, _ -> nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(
            Route.CELL,
            arguments = listOf(
                navArgument("athleteId") { type = NavType.LongType },
                navArgument("testKey") { type = NavType.StringType },
            ),
        ) { entry ->
            val aId = entry.arguments?.getLong("athleteId") ?: 0L
            val t = BatteryTest.fromKey(entry.arguments?.getString("testKey").orEmpty())
            val sId = vm.session.value.sessionId
            TestCellPlaceholder(
                athleteId = aId,
                test = t,
                onRecord = { t?.let { nav.navigate(Route.capture(aId, it.key, sId)) } },
                onBack = { nav.popBackStack() },
            )
        }
    }
}

/**
 * Placeholder for the per-test capture and measurement screen.
 *
 * Phase 3 replaces this with protocol guidance, the capture-guidance wizard,
 * CameraX recording enforcing the §6.3.1 duration gate, and the measurement
 * result with its confidence band.
 */
@Composable
private fun TestCellPlaceholder(
    athleteId: Long,
    test: BatteryTest?,
    onRecord: () -> Unit,
    onBack: () -> Unit,
) {
    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                test?.let { "${it.number}. ${it.label()}" } ?: "Test",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Athlete #$athleteId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            test?.let {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spec("Attempts", "${it.attempts}")
                        Spec("Unit", it.unit)
                        Spec(
                            "Capture",
                            if (it.isPhoto) "single photo"
                            else "${it.minDurationSec}–${it.maxDurationSec}s video",
                        )
                        Spec("Measurement", it.measurement.describe())
                    }
                }
            }

            androidx.compose.material3.Button(
                onClick = onRecord,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Record attempt") }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}

/** Plain-language description of how a result is produced. */
private fun Measurement.describe(): String = when (this) {
    Measurement.AI -> "on-device AI"
    Measurement.AI_PLANNED -> "manual for now — AI method designed, not yet built"
    Measurement.OCR_ASSIST -> "manual entry, OCR reads the scale display"
    Measurement.MANUAL -> "manual entry with protocol guidance"
}

@Composable
private fun Spec(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}
