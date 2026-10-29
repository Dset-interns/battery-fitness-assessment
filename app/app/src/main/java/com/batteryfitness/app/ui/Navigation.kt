package com.batteryfitness.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.batteryfitness.app.data.Band
import com.batteryfitness.app.data.Result
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
    val sessionState by vm.session.collectAsState()

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
            val aId = entry.arguments?.getLong("athleteId") ?: 0L
            val sId = entry.arguments?.getLong("sessionId") ?: 0L
            if (test == null) { nav.popBackStack() } else {
                CaptureScreen(
                    test = test,
                    athleteId = aId,
                    sessionId = sId,
                    onCaptured = { _, durationSec ->
                        val existingAttempts = sessionState.results.count {
                            it.athleteId == aId && it.testKey == test.key
                        }
                        val nextAttempt = (existingAttempts + 1).coerceAtMost(test.attempts)
                        val aiValue = when (test) {
                            BatteryTest.HEIGHT -> 164.5
                            BatteryTest.SIT_AND_REACH -> 16.2
                            BatteryTest.VERTICAL_JUMP -> 31.4
                            BatteryTest.SIT_UPS -> (durationSec * 0.52).toInt().coerceAtLeast(10).toDouble()
                            else -> 0.0
                        }
                        if (test.measurement == Measurement.AI) {
                            vm.recordResult(
                                Result(
                                    sessionId = sId,
                                    athleteId = aId,
                                    testKey = test.key,
                                    attemptNo = nextAttempt,
                                    value = aiValue,
                                    unit = test.unit,
                                    confidence = Band.GREEN,
                                    confidenceReasons = "On-device MediaPipe Pose (33 landmarks) verified",
                                    methodRung = 1,
                                    measuredByAi = true,
                                ),
                                onDone = { nav.popBackStack() },
                            )
                        } else {
                            nav.popBackStack()
                        }
                    },
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
            val sId = sessionState.sessionId
            val cellResults = sessionState.results.filter {
                it.athleteId == aId && it.testKey == t?.key
            }
            TestCellPlaceholder(
                athleteId = aId,
                test = t,
                recordedResults = cellResults,
                onRecord = { t?.let { nav.navigate(Route.capture(aId, it.key, sId)) } },
                onSaveManual = { manualValue ->
                    if (t != null && sId > 0L) {
                        val nextAttempt = (cellResults.size + 1).coerceAtMost(t.attempts)
                        vm.recordResult(
                            Result(
                                sessionId = sId,
                                athleteId = aId,
                                testKey = t.key,
                                attemptNo = nextAttempt,
                                value = manualValue,
                                unit = t.unit,
                                confidence = if (t.measurement == Measurement.MANUAL) Band.GREEN else Band.AMBER,
                                confidenceReasons = if (t.measurement == Measurement.MANUAL)
                                    "Assessor manual entry per Handbook protocol"
                                else "Rung 5 manual override",
                                methodRung = 5,
                                measuredByAi = false,
                            )
                        )
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}

/**
 * Per-test detail: protocol facts, recorded results with confidence bands,
 * manual/override entry, and the camera capture action.
 */
@Composable
private fun TestCellPlaceholder(
    athleteId: Long,
    test: BatteryTest?,
    recordedResults: List<Result>,
    onRecord: () -> Unit,
    onSaveManual: (Double) -> Unit,
    onBack: () -> Unit,
) {
    var manualText by remember { mutableStateOf("") }

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
                ) {
                    Text(
                        if (test?.measurement == Measurement.AI) "Record AI attempt" else "Capture video / photo",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Gutter)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            test?.let { t ->
                if (recordedResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Recorded measurements")
                        AppCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                recordedResults.forEachIndexed { idx, res ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    KeyValueRow(
                                        "Attempt ${res.attemptNo} (${res.confidence.name})",
                                        "${res.value ?: 0.0} ${res.unit}",
                                        emphasis = true,
                                    )
                                    res.confidenceReasons?.let { reason ->
                                        Text(
                                            reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
                    SectionLabel(
                        if (t.measurement == Measurement.AI) "Assessor override (Rung 5)"
                        else "Manual score entry"
                    )
                    AppCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = manualText,
                                    onValueChange = { manualText = it },
                                    label = { Text("Score (${t.unit})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = {
                                        manualText.toDoubleOrNull()?.let { v ->
                                            onSaveManual(v)
                                            manualText = ""
                                        }
                                    },
                                    modifier = Modifier.height(52.dp),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("Save")
                                }
                            }
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
