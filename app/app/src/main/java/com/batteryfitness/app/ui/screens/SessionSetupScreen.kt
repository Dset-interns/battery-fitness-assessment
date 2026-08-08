package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteryfitness.app.data.Sequencing
import com.batteryfitness.app.data.SessionMode
import com.batteryfitness.app.ui.AppViewModel

/**
 * Session setup — the assessor picks the mode and the sequencing before any
 * capture begins (§6.1, §6.5).
 *
 * The sequencing choice is presented with its real-world consequence rather
 * than as an opaque toggle, because §6.5 is explicit that test-wise "mirrors
 * real ground practice… and significantly reduces total assessment time" —
 * the station is set up once per test instead of once per athlete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupScreen(
    vm: AppViewModel,
    onStarted: (Long) -> Unit,
    onRegisterAthlete: () -> Unit,
    onBack: () -> Unit,
) {
    val athletes by vm.athletes.collectAsState(initial = emptyList())
    var mode by remember { mutableStateOf(SessionMode.COACH) }
    var sequencing by remember { mutableStateOf(Sequencing.TEST_WISE) }
    val selected = remember { mutableStateListOf<Long>() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Start assessment session") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Mode", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == SessionMode.COACH,
                    onClick = { mode = SessionMode.COACH },
                    label = { Text("Coach / Assessor") },
                )
                FilterChip(
                    selected = mode == SessionMode.INDIVIDUAL,
                    onClick = {
                        mode = SessionMode.INDIVIDUAL
                        // Individual mode is one athlete assessing themselves.
                        while (selected.size > 1) selected.removeAt(selected.lastIndex)
                    },
                    label = { Text("Individual athlete") },
                )
            }

            if (mode == SessionMode.COACH) {
                Text("Sequencing", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sequencing == Sequencing.TEST_WISE,
                        onClick = { sequencing = Sequencing.TEST_WISE },
                        label = { Text("Test-wise (station)") },
                    )
                    FilterChip(
                        selected = sequencing == Sequencing.ATHLETE_WISE,
                        onClick = { sequencing = Sequencing.ATHLETE_WISE },
                        label = { Text("Athlete-wise") },
                    )
                }
                Text(
                    when (sequencing) {
                        Sequencing.TEST_WISE ->
                            "One test is run across the whole roster, then the next. " +
                                "Set the station up once per test — faster on the ground."
                        Sequencing.ATHLETE_WISE ->
                            "Each athlete completes every test before the next begins."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    "Either order produces identical, correctly indexed results, " +
                        "and you can switch part-way without losing work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Roster (${selected.size} selected)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onRegisterAthlete) { Text("+ Athlete") }
            }

            if (athletes.isEmpty()) {
                Text(
                    "No athletes registered yet. Add one to begin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(athletes) { athlete ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = athlete.id in selected,
                                onCheckedChange = { on ->
                                    if (on) {
                                        if (mode == SessionMode.INDIVIDUAL) selected.clear()
                                        selected.add(athlete.id)
                                    } else selected.remove(athlete.id)
                                },
                            )
                            Column {
                                Text(athlete.name,
                                    style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${athlete.ageYears} yrs · " +
                                        athlete.gender.name.lowercase() +
                                        (athlete.school?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    vm.startSession(mode, sequencing, selected.toList(), null) {
                        onStarted(it)
                    }
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) { Text("Start session") }
        }
    }
}
