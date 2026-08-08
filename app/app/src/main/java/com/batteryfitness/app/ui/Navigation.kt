package com.batteryfitness.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.batteryfitness.app.ui.components.AppCard
import com.batteryfitness.app.ui.components.AppScaffold
import com.batteryfitness.app.ui.components.BottomActionBar
import com.batteryfitness.app.ui.components.Gutter
import com.batteryfitness.app.ui.components.KeyValueRow
import com.batteryfitness.app.ui.components.SectionLabel

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
 * Per-test detail: protocol facts, then the capture action.
 *
 * Phase 4 adds the capture-guidance wizard and the measurement result with its
 * confidence band above the action bar.
 */
@Composable
private fun TestCellPlaceholder(
    athleteId: Long,
    test: BatteryTest?,
    onRecord: () -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(
        title = test?.let { "${it.number}. ${it.label()}" } ?: "Test",
        subtitle = "Athlete #$athleteId",
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                Button(
                    onClick = onRecord,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Record attempt", fontWeight = FontWeight.SemiBold) }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Gutter)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            test?.let { t ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Protocol")
                    AppCard {
                        Column(Modifier.padding(16.dp)) {
                            KeyValueRow("Attempts permitted", "${t.attempts}")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            KeyValueRow("Recorded in", t.unit)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            KeyValueRow(
                                "Capture",
                                if (t.isPhoto) "Single photo"
                                else "${t.minDurationSec}–${t.maxDurationSec} s video",
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            KeyValueRow(
                                "Measurement",
                                t.measurement.describe(),
                                emphasis = t.measurement == Measurement.AI,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Capture requirements")
                    AppCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Recording shorter than ${t.minDurationSec} s is rejected at " +
                                    "the point of capture, and recording stops automatically " +
                                    "at ${t.maxDurationSec} s.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Minimum 720p at 30 fps. Every capture is tagged with the test, " +
                                    "athlete, timestamp and location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Plain-language description of how a result is produced. */
private fun Measurement.describe(): String = when (this) {
    Measurement.AI -> "On-device AI"
    Measurement.AI_PLANNED -> "Manual entry — AI method designed"
    Measurement.OCR_ASSIST -> "Manual entry, OCR assist"
    Measurement.MANUAL -> "Manual entry"
}
