package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryfitness.app.BuildConfig
import com.batteryfitness.app.R
import com.batteryfitness.app.domain.BatteryTest
import com.batteryfitness.app.domain.Measurement
import com.batteryfitness.app.ui.AppLanguage
import com.batteryfitness.app.ui.LanguageController
import com.batteryfitness.app.ui.theme.AppBackground
import com.batteryfitness.app.ui.theme.IndiaGreen

@Composable
fun HomeScreen(
    languages: LanguageController,
    onStartSession: () -> Unit,
    onRegister: () -> Unit,
) {
    val context = LocalContext.current
    val active by languages.current
    val aiCount = BatteryTest.aiMeasured.size
    val plannedCount = BatteryTest.aiPlanned.size

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---- Brand header -----------------------------------------
            // DSeT's own mark. The programme is credited as TEXT rather than
            // with SAI's emblem: this is a competing team's prototype, and an
            // official emblem in the header would read as a government-issued
            // app. Swap in the real asset once one is supplied and its use is
            // permitted.
            Column {
                // The mark already carries the "DSeT CONSULTING" wordmark, so no
                // duplicate text line beside it.
                Image(
                    painter = painterResource(R.drawable.dset_logo),
                    contentDescription = "DSeT Consulting",
                    modifier = Modifier
                        .height(62.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Built for the National Hackathon · NeGD · MeitY · MYAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            // ---- Hero -------------------------------------------------
            Text(
                "Battery Fitness",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "ASSESSMENT",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )

            Text(
                "India's 10-test physical fitness battery, measured on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            )

            // ---- Language ---------------------------------------------
            // §7.3 makes Hindi + English a non-negotiable constraint. The app
            // defaults to English and switches only on an explicit choice, so a
            // demo is predictable rather than dependent on the device locale.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = active == lang,
                        onClick = { languages.set(context, lang) },
                        label = { Text(lang.endonym) },
                    )
                }
            }

            // ---- Guarantees strip -------------------------------------
            // These three are the app's actual constraints, not marketing:
            // §6.1 fully offline, §6.2 on-device inference, §7.1 no cloud.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuaranteeRow("Works with no internet", "Full battery, results and report card")
                    GuaranteeRow(
                        "AI runs on this phone",
                        "$aiCount tests measured from video today, " +
                            "$plannedCount more designed",
                    )
                    GuaranteeRow("Video never leaves the device", "No cloud upload of athlete footage")
                }
            }

            Spacer(Modifier.height(2.dp))

            // ---- Primary actions --------------------------------------
            Button(
                onClick = onStartSession,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    "Start assessment session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            OutlinedButton(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Register an athlete", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(4.dp))

            // ---- Test coverage overview -------------------------------
            Text(
                "TEST COVERAGE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    BatteryTest.entries.forEach { test -> TestRow(test) }
                }
            }

            // ---- About (Submission §2.2) ------------------------------
            Text(
                "${BuildConfig.TEAM_NAME}  ·  v${BuildConfig.VERSION_NAME}  ·  " +
                    BuildConfig.BUILD_DATE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GuaranteeRow(title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = IndiaGreen, content = {}, modifier = Modifier.size(8.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TestRow(test: BatteryTest) {
    // Four distinct states, not two. Showing "AI planned" separately from "AI"
    // presents a credible roadmap without claiming anything unbuilt — which is
    // what §8.5 rewards and what Round 2 would otherwise expose.
    val (badge, isBuilt) = when (test.measurement) {
        Measurement.AI -> "AI" to true
        Measurement.AI_PLANNED -> "AI planned" to false
        Measurement.OCR_ASSIST -> "OCR assist" to false
        Measurement.MANUAL -> "Manual" to false
    }
    val isAi = isBuilt
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            test.number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        Text(
            test.label(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isAi) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isAi) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
