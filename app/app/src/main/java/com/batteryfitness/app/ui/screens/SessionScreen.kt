package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteryfitness.app.data.Sequencing
import com.batteryfitness.app.domain.BatteryTest
import com.batteryfitness.app.domain.Measurement
import com.batteryfitness.app.ui.AppViewModel

/**
 * The working screen during a session.
 *
 * It renders whichever axis the assessor is currently working along — tests
 * when in station mode, athletes when in athlete-wise mode — but both views
 * read the same completion set. That is the visible consequence of sequencing
 * being only a traversal order (§6.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    vm: AppViewModel,
    onOpenCell: (athleteId: Long, test: BatteryTest) -> Unit,
    onBack: () -> Unit,
) {
    val s by vm.session.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Session ${s.sessionId}")
                    Text(
                        "${s.progress} complete · ${s.roster.size} athletes",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            })
        },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LinearProgressIndicator(
                progress = { s.progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )

            // Switching mid-session is supported and safe — nothing already
            // recorded is re-requested. See SessionEngineTest.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.sequencing == Sequencing.TEST_WISE,
                    onClick = { vm.setSequencing(Sequencing.TEST_WISE) },
                    label = { Text("By test") },
                )
                FilterChip(
                    selected = s.sequencing == Sequencing.ATHLETE_WISE,
                    onClick = { vm.setSequencing(Sequencing.ATHLETE_WISE) },
                    label = { Text("By athlete") },
                )
            }

            s.nextCell?.let { next ->
                val who = s.athlete(next.athleteId)?.name ?: "athlete"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text(
                            "${next.test.number}. ${next.test.label()} — $who",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            } ?: Card(Modifier.fillMaxWidth()) {
                Text(
                    "Battery complete for every athlete on this roster.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            when (s.sequencing) {
                Sequencing.TEST_WISE -> {
                    Text("Stations", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    val perTest = s.perTest
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.tests) { test ->
                            val p = perTest[test]
                            RowCard(
                                title = "${test.number}. ${test.label()}",
                                subtitle = buildString {
                                    append(if (test.measurement == Measurement.AI) "AI measured" else "Manual entry")
                                    append(" · ")
                                    append(if (test.isPhoto) "photo" else "${test.minDurationSec}–${test.maxDurationSec}s")
                                },
                                trailing = p?.toString() ?: "",
                                done = p?.complete == true,
                                onClick = {
                                    val target = s.roster.firstOrNull { a ->
                                        com.batteryfitness.app.domain.Cell(a.id, test) !in s.completed
                                    } ?: s.roster.firstOrNull()
                                    target?.let { onOpenCell(it.id, test) }
                                },
                            )
                        }
                    }
                }

                Sequencing.ATHLETE_WISE -> {
                    Text("Athletes", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    val perAthlete = s.perAthlete
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.roster) { athlete ->
                            val p = perAthlete[athlete.id]
                            RowCard(
                                title = athlete.name,
                                subtitle = "${athlete.ageYears} yrs · " +
                                    athlete.gender.name.lowercase(),
                                trailing = p?.toString() ?: "",
                                done = p?.complete == true,
                                onClick = {
                                    val test = s.tests.firstOrNull { t ->
                                        com.batteryfitness.app.domain.Cell(athlete.id, t) !in s.completed
                                    } ?: s.tests.first()
                                    onOpenCell(athlete.id, test)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowCard(
    title: String,
    subtitle: String,
    trailing: String,
    done: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (done)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Text(
                if (done) "✓ $trailing" else trailing,
                style = MaterialTheme.typography.labelLarge,
                color = if (done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

