package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.batteryfitness.app.data.Gender
import com.batteryfitness.app.data.IdType
import com.batteryfitness.app.ui.AppViewModel
import com.batteryfitness.app.ui.components.AppCard
import com.batteryfitness.app.ui.components.AppScaffold
import com.batteryfitness.app.ui.components.BottomActionBar
import com.batteryfitness.app.ui.components.Gutter
import com.batteryfitness.app.ui.components.SectionLabel

/**
 * Athlete registration.
 *
 * §6.5 expects registration via APAAR / Aadhaar / NSRS with linked metadata
 * pre-populated from those systems. Those integrations are design-level for now
 * (Submission §4.1.6 accepts that), so the identifier is captured and typed
 * here and the prefill hook has a single call site.
 *
 * Works with no network: a coach in a village must be able to register athletes
 * and run the whole battery offline (§6.1).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterAthleteScreen(
    vm: AppViewModel,
    onRegistered: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.MALE) }
    var idType by remember { mutableStateOf(IdType.LOCAL) }
    var externalId by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }

    val ageValue = age.toIntOrNull()
    val canSubmit = name.isNotBlank() && ageValue != null && ageValue in 5..25

    AppScaffold(
        title = "Register athlete",
        subtitle = "Added to the roster for this session",
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                Button(
                    onClick = {
                        vm.registerAthlete(
                            name = name,
                            ageYears = ageValue ?: 0,
                            gender = gender,
                            externalId = externalId.ifBlank { null },
                            idType = idType,
                            school = school,
                        ) { onRegistered() }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Register athlete") }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---- Identity -------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Athlete details")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter(Char::isDigit).take(2) },
                    label = { Text("Age in years") },
                    // Age is not cosmetic: it branches real protocol. Say so,
                    // because an assessor who mistypes it changes the test.
                    supportingText = {
                        Text("Sets the sit-up window, endurance distance and medicine-ball mass")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Gender", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gender.entries.forEach { g ->
                            FilterChip(
                                selected = gender == g,
                                onClick = { gender = g },
                                label = {
                                    Text(g.name.lowercase().replaceFirstChar { it.uppercase() })
                                },
                            )
                        }
                    }
                }
            }

            // ---- Programme identifier -------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Programme identifier")
                // FlowRow, not Row: four options do not fit one line at 1080px,
                // and a fixed Row pushed "None" off-screen where it could not be
                // selected at all.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdType.entries.forEach { t ->
                        FilterChip(
                            selected = idType == t,
                            onClick = { idType = t },
                            label = { Text(if (t == IdType.LOCAL) "None" else t.name) },
                        )
                    }
                }
                if (idType != IdType.LOCAL) {
                    OutlinedTextField(
                        value = externalId,
                        onValueChange = { externalId = it },
                        label = { Text("${idType.name} number") },
                        supportingText = {
                            Text(
                                if (idType == IdType.NSRS) {
                                    "Existing NSRS ID only. New IDs are issued after the " +
                                        "battery is completed, not at registration."
                                } else {
                                    "Linked profile data is fetched when connectivity allows"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    AppCard {
                        Text(
                            "No identifier recorded. The athlete can still be assessed; " +
                                "an ID can be linked later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            // ---- Institution ----------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Institution")
                OutlinedTextField(
                    value = school,
                    onValueChange = { school = it },
                    label = { Text("School or centre") },
                    supportingText = { Text("Optional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
