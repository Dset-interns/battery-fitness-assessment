package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.batteryfitness.app.data.Gender
import com.batteryfitness.app.data.IdType
import com.batteryfitness.app.ui.AppViewModel

/**
 * Athlete registration.
 *
 * §6.5 step 1 expects registration via APAAR / Aadhaar / NSRS, with linked
 * metadata pre-populated from those systems. Those integrations are
 * design-level for now (Submission §4.1.6 accepts that), so the ID is captured
 * and typed here and the prefill hook is a single call site.
 *
 * Registration deliberately works with no network: a coach in a village must be
 * able to register athletes and run the whole battery offline (§6.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Register athlete") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = age, onValueChange = { age = it.filter(Char::isDigit).take(2) },
                label = { Text("Age (years)") },
                supportingText = {
                    // The age drives real protocol branches, so it is not cosmetic:
                    // sit-ups 30s vs 45s, endurance 800m vs 1.6km, ball 1kg vs 2kg.
                    Text("Sets the sit-up window, endurance distance and medicine-ball mass")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Gender", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gender.entries.forEach { g ->
                    FilterChip(
                        selected = gender == g,
                        onClick = { gender = g },
                        label = { Text(g.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Text("Identity", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    value = externalId, onValueChange = { externalId = it },
                    label = { Text("${idType.name} ID") },
                    supportingText = {
                        Text(
                            if (idType == IdType.NSRS)
                                "Existing NSRS ID only. New IDs are issued after the " +
                                    "battery is completed, not at registration."
                            else
                                "Linked profile data is fetched when connectivity allows"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = school, onValueChange = { school = it },
                label = { Text("School / centre (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Register") }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Back") }
        }
    }
}
