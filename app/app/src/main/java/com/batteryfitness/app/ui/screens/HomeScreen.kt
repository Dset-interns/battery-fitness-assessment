package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteryfitness.app.BuildConfig
import com.batteryfitness.app.R
import com.batteryfitness.app.domain.BatteryTest
import com.batteryfitness.app.domain.Measurement
import com.batteryfitness.app.ui.AppLanguage
import com.batteryfitness.app.ui.LanguageController
import com.batteryfitness.app.ui.components.AppCard
import com.batteryfitness.app.ui.components.AppScaffold
import com.batteryfitness.app.ui.components.Dot
import com.batteryfitness.app.ui.components.Gutter
import com.batteryfitness.app.ui.components.SectionLabel
import com.batteryfitness.app.ui.components.StatusPill
import com.batteryfitness.app.ui.theme.IndiaGreen

@Composable
fun HomeScreen(
    languages: LanguageController,
    onStartSession: () -> Unit,
    onRegister: () -> Unit,
) {
    val context = LocalContext.current
    val active by languages.current
    val built = BatteryTest.aiMeasured.size
    val planned = BatteryTest.aiPlanned.size

    AppScaffold(
        title = "Battery Fitness Assessment",
        subtitle = "10-test physical fitness battery",
        actions = {
            // Language belongs in the bar as a persistent control. Previously it
            // sat mid-content as two chips, competing with the primary action.
            TextButton(onClick = {
                languages.set(
                    context,
                    if (active == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH,
                )
            }) {
                Text(
                    if (active == AppLanguage.ENGLISH) "हिन्दी" else "English",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter)
                .padding(top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ---- Primary actions, above the fold ----------------------
            // The two things an assessor opens the app to do. They were
            // previously below a hero block and a guarantees card, which is the
            // wrong order for a tool used standing on a field.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
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
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Register an athlete", style = MaterialTheme.typography.titleSmall)
                }
            }

            // ---- Operating conditions ---------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Operating conditions")
                AppCard {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Guarantee(
                            "Works with no internet",
                            "Registration, capture, scoring and report card",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Guarantee(
                            "AI runs on this phone",
                            "$built tests measured from video · $planned more designed",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Guarantee(
                            "Video never leaves the device",
                            "No cloud upload of athlete footage",
                        )
                    }
                }
            }

            // ---- Coverage ---------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SectionLabel("Test coverage")
                    Text(
                        "$built of ${BatteryTest.entries.size} AI-measured",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
                AppCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        BatteryTest.entries.forEachIndexed { i, test ->
                            if (i > 0) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            TestRow(test)
                        }
                    }
                }
            }

            // ---- Attribution ------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.dset_logo),
                        contentDescription = "DSeT Consulting",
                        modifier = Modifier.height(34.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Submitted for the National Hackathon on",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        Text(
                            "AI-Powered Battery Fitness Assessment",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        )
                        Text(
                            "NeGD · MeitY · MYAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
                Text(
                    "v${BuildConfig.VERSION_NAME}  ·  build ${BuildConfig.BUILD_DATE}  ·  " +
                        BuildConfig.TEAM_NAME,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun Guarantee(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.padding(top = 5.dp)) { Dot(IndiaGreen) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TestRow(test: BatteryTest) {
    val (badge, isBuilt) = when (test.measurement) {
        Measurement.AI -> "AI" to true
        Measurement.AI_PLANNED -> "Planned" to false
        Measurement.OCR_ASSIST -> "OCR" to false
        Measurement.MANUAL -> "Manual" to false
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(test.number),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.width(28.dp),
        )
        Text(
            test.label(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        StatusPill(
            text = badge,
            container = if (isBuilt) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isBuilt) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
