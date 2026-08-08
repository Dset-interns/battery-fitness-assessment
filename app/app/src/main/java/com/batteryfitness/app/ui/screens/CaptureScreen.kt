package com.batteryfitness.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.batteryfitness.app.capture.CaptureFacts
import com.batteryfitness.app.capture.CaptureQualityGate
import com.batteryfitness.app.capture.VideoRecorder
import com.batteryfitness.app.domain.BatteryTest
import java.io.File
import kotlinx.coroutines.delay

/**
 * Capture screen for one test attempt.
 *
 * The §6.3.1 rules are enforced *live*, not just after the fact:
 *  - Stop stays disabled until the per-test minimum is reached, so the operator
 *    cannot produce a clip that would only be rejected afterwards.
 *  - A warning appears before the maximum.
 *  - Recording auto-stops at the maximum with a notice, rather than silently
 *    truncating the attempt.
 *
 * Doing it live matters on the ground: §8.3 assumes untrained operators, and
 * telling a PE teacher their 12-second sit-up clip is invalid *after* the
 * athlete has finished means redoing the whole attempt.
 */
@Composable
fun CaptureScreen(
    test: BatteryTest,
    athleteId: Long,
    sessionId: Long,
    onCaptured: (File, CaptureFacts) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val name = test.label()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> hasCamera = granted[Manifest.permission.CAMERA] == true }

    LaunchedEffect(Unit) {
        if (!hasCamera) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    // §6.3 requires a GPS tag on every capture. Denial is not
                    // fatal — a village ground may have no fix at all.
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }

    if (!hasCamera) {
        PermissionPrompt(name, onGrant = {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }, onBack = onBack)
        return
    }

    val recorder = remember { VideoRecorder(context, lifecycleOwner) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var verdict by remember { mutableStateOf<CaptureQualityGate.Verdict?>(null) }
    var target by remember { mutableStateOf<File?>(null) }

    // The PreviewView must exist before CameraX can bind to it, so it is
    // created here and bound once from a LaunchedEffect rather than inside the
    // AndroidView update block (which runs on every recomposition).
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var bound by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(previewView) {
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        bound = recorder.bind(preview)
        if (!bound) bindError = "Camera could not be opened on this device."
    }

    DisposableEffect(Unit) { onDispose { recorder.release() } }

    // Elapsed-time ticker driving the live gate.
    LaunchedEffect(recording) {
        if (!recording) { elapsed = 0; return@LaunchedEffect }
        while (recording) {
            delay(1000)
            elapsed += 1
            if (CaptureQualityGate.mustAutoStop(test, elapsed)) {
                recorder.stop()
                recording = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {

        // ---- Preview -------------------------------------------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewView },
            )

            bindError?.let { msg ->
                Text(
                    msg,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            // Live status overlay
            Column(Modifier.align(Alignment.TopStart).padding(14.dp)) {
                Text(
                    "${test.number}. $name",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (test.isPhoto) "Single photo"
                    else "${test.minDurationSec}–${test.maxDurationSec}s required",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (recording) {
                Column(
                    Modifier.align(Alignment.TopEnd).padding(14.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        "● ${elapsed}s",
                        color = if (CaptureQualityGate.shouldWarn(test, elapsed))
                            Color(0xFFFFB74D) else Color(0xFFFF5252),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!CaptureQualityGate.canStop(test, elapsed)) {
                        Text(
                            "${test.minDurationSec - elapsed}s to minimum",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (recording && CaptureQualityGate.shouldWarn(test, elapsed)) {
                Card(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB26A00)),
                ) {
                    Text(
                        "Stopping automatically at ${test.maxDurationSec}s",
                        Modifier.padding(12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // ---- Controls -------------------------------------------------
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF11151C)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (recording && test.maxDurationSec > 0) {
                LinearProgressIndicator(
                    progress = { elapsed.toFloat() / test.maxDurationSec },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            verdict?.let { v ->
                val (title, body, colour) = when (v) {
                    is CaptureQualityGate.Verdict.Rejected ->
                        Triple(v.reason, v.detail, Color(0xFFB3261E))
                    is CaptureQualityGate.Verdict.AcceptedWithWarning ->
                        Triple("Accepted", v.warning, Color(0xFFB26A00))
                    CaptureQualityGate.Verdict.Accepted ->
                        Triple("Accepted", "Recording meets all capture requirements.",
                            Color(0xFF138808))
                }
                Card(colors = CardDefaults.cardColors(containerColor = colour)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(title, color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        Text(body, color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Back", color = Color.White)
                }
                Button(
                    onClick = {
                        if (!recording) {
                            verdict = null
                            val file = recorder.outputFileFor(test, athleteId, sessionId)
                            target = file
                            recorder.start(file, withAudio = true) { event ->
                                if (event is VideoRecordEvent.Finalize) {
                                    val facts = recorder.probe(file)
                                    verdict = CaptureQualityGate.evaluate(
                                        test = test,
                                        durationSec = facts.durationSec,
                                        sizeBytes = facts.sizeBytes,
                                        widthPx = facts.widthPx,
                                        heightPx = facts.heightPx,
                                        fps = facts.fps,
                                        testDisplayName = name,
                                    )
                                    if (verdict !is CaptureQualityGate.Verdict.Rejected) {
                                        onCaptured(file, facts)
                                    } else {
                                        // §6.3.1: reject AT the point of capture.
                                        file.delete()
                                    }
                                }
                            }
                            recording = true
                        } else {
                            recorder.stop()
                            recording = false
                        }
                    },
                    enabled = bound && (!recording || CaptureQualityGate.canStop(test, elapsed)),
                    modifier = Modifier.weight(2f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) Color(0xFFB3261E)
                        else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        when {
                            !recording -> "Start recording"
                            !CaptureQualityGate.canStop(test, elapsed) ->
                                "Minimum ${test.minDurationSec}s"
                            else -> "Stop"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(name: String, onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Camera access needed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text(
            "Recording the $name test requires the camera. Video is analysed " +
                "on this device and never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text("Grant camera access")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
