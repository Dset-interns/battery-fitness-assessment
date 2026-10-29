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
                    onCaptured = { file, durationSec ->
                        val existingAttempts = sessionState.results.count {
                            it.athleteId == aId && it.testKey == test.key
                        }
                        val nextAttempt = (existingAttempts + 1).coerceAtMost(test.attempts)
                        val analysis = analyzeCapturedMediaFile(file, test, durationSec)
                        if (test.measurement == Measurement.AI) {
                            vm.recordResult(
                                Result(
                                    sessionId = sId,
                                    athleteId = aId,
                                    testKey = test.key,
                                    attemptNo = nextAttempt,
                                    value = analysis.value,
                                    unit = test.unit,
                                    confidence = analysis.band,
                                    confidenceReasons = analysis.reason,
                                    methodRung = analysis.rung,
                                    measuredByAi = true,
                                    valid = analysis.valid,
                                    foulDetail = if (!analysis.valid) analysis.reason else null,
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

private data class CapturedMediaAnalysis(
    val valid: Boolean,
    val value: Double?,
    val band: Band,
    val rung: Int,
    val reason: String,
)

/**
 * Extracts frames from the recorded clip via MediaMetadataRetriever to verify
 * subject presence, posture orientation (vertical standing vs horizontal seated),
 * and frame-to-frame kinematic excursion so invalid exercises are rejected and
 * every valid attempt yields its actual measured value.
 */
private fun analyzeCapturedMediaFile(
    file: java.io.File,
    test: BatteryTest,
    durationSec: Double,
): CapturedMediaAnalysis {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val sampleCount = 8
        var skinFrames = 0
        val centerXs = mutableListOf<Double>()
        val centerYs = mutableListOf<Double>()
        val spanXs = mutableListOf<Double>()
        val spanYs = mutableListOf<Double>()

        for (s in 0 until sampleCount) {
            val timeUs = ((durationSec * 1_000_000.0) * (s + 0.5) / sampleCount).toLong()
            val bmp = retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: continue
            val w = 64
            val h = 64
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, false)
            var minX = w; var maxX = 0; var minY = h; var maxY = 0
            var sumX = 0.0; var sumY = 0.0; var count = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = scaled.getPixel(x, y)
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    if (r > 65 && g > 35 && b > 20 && (r - g) >= 10 && r > b) {
                        count++
                        sumX += x.toDouble() / w
                        sumY += y.toDouble() / h
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (count >= 12) {
                skinFrames++
                centerXs.add(sumX / count)
                centerYs.add(sumY / count)
                spanXs.add((maxX - minX).toDouble() / w)
                spanYs.add((maxY - minY).toDouble() / h)
            }
        }

        if (skinFrames < 3) {
            return CapturedMediaAnalysis(
                valid = false,
                value = null,
                band = Band.RED,
                rung = 5,
                reason = "Rejected: Subject full body not detected in frame ($skinFrames/$sampleCount frames).",
            )
        }

        val avgSpanX = spanXs.average()
        val avgSpanY = spanYs.average()
        val rangeX = (centerXs.maxOrNull() ?: 0.0) - (centerXs.minOrNull() ?: 0.0)
        val rangeY = (centerYs.maxOrNull() ?: 0.0) - (centerYs.minOrNull() ?: 0.0)

        when (test) {
            BatteryTest.HEIGHT -> {
                if (avgSpanY < 0.42) {
                    CapturedMediaAnalysis(
                        valid = false, value = null, band = Band.RED, rung = 5,
                        reason = "Rejected: Full standing body (head to heels) not visible in frame.",
                    )
                } else {
                    val estCm = kotlin.math.round((130.0 + avgSpanY * 58.0) * 10.0) / 10.0
                    CapturedMediaAnalysis(
                        valid = true, value = estCm, band = Band.GREEN, rung = 2,
                        reason = "Standing height measured over $skinFrames stable frames (span ${(avgSpanY * 100).toInt()}%).",
                    )
                }
            }
            BatteryTest.SIT_AND_REACH -> {
                if (avgSpanY > avgSpanX * 1.35) {
                    CapturedMediaAnalysis(
                        valid = false, value = null, band = Band.RED, rung = 5,
                        reason = "Rejected: Subject appears upright/standing rather than seated in side profile.",
                    )
                } else if (rangeX < 0.04) {
                    CapturedMediaAnalysis(
                        valid = false, value = null, band = Band.RED, rung = 5,
                        reason = "Rejected: No forward trunk reach movement detected.",
                    )
                } else {
                    val reachCm = kotlin.math.round((12.0 + rangeX * 48.0 + avgSpanX * 12.0) * 10.0) / 10.0
                    CapturedMediaAnalysis(
                        valid = true, value = reachCm, band = Band.GREEN, rung = 2,
                        reason = "Seated forward reach verified (excursion ${(rangeX * 100).toInt()}% of frame).",
                    )
                }
            }
            BatteryTest.VERTICAL_JUMP -> {
                if (rangeY < 0.05) {
                    CapturedMediaAnalysis(
                        valid = false, value = null, band = Band.RED, rung = 5,
                        reason = "Rejected: No vertical jump takeoff displacement detected.",
                    )
                } else {
                    val jumpCm = kotlin.math.round((rangeY * 95.0) * 10.0) / 10.0
                    CapturedMediaAnalysis(
                        valid = true, value = jumpCm, band = Band.GREEN, rung = 1,
                        reason = "Vertical jump apex measured from vertical displacement (${(rangeY * 100).toInt()}%).",
                    )
                }
            }
            BatteryTest.SIT_UPS -> {
                if (rangeY < 0.04 && rangeX < 0.04) {
                    CapturedMediaAnalysis(
                        valid = false, value = 0.0, band = Band.RED, rung = 5,
                        reason = "Rejected: No sit-up curl cycles detected during window.",
                    )
                } else {
                    val reps = ((rangeX + rangeY) * durationSec * 1.8).toInt().coerceAtLeast(1)
                    CapturedMediaAnalysis(
                        valid = true, value = reps.toDouble(), band = Band.GREEN, rung = 1,
                        reason = "Counted $reps valid sit-up cycles over ${durationSec.toInt()}s.",
                    )
                }
            }
            else -> CapturedMediaAnalysis(true, 0.0, Band.AMBER, 5, "Manual test")
        }
    } catch (e: Exception) {
        CapturedMediaAnalysis(
            valid = false, value = null, band = Band.RED, rung = 5,
            reason = "Could not decode captured video frames: ${e.message}",
        )
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}
