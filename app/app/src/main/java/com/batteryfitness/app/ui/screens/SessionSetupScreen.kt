package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteryfitness.app.data.Sequencing
import com.batteryfitness.app.data.SessionMode
import com.batteryfitness.app.ui.AppViewModel
import com.batteryfitness.app.ui.components.AppCard
import com.batteryfitness.app.ui.components.AppScaffold
import com.batteryfitness.app.ui.components.BottomActionBar
import com.batteryfitness.app.ui.components.Gutter
import com.batteryfitness.app.ui.components.SectionLabel

/**
 * Session setup — mode and sequencing chosen before any capture (§6.1, §6.5).
 *
 * Segmented buttons rather than chips: the options are mutually exclusive and
 * of equal weight, and the previous FilterChips wrapped mid-word on a 1080px
 * screen ("Athlete-wi / se"), which looks broken.
 */
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

    AppScaffold(
        title = "New session",
        subtitle = "${selected.size} of ${athletes.size} athletes selected",
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                OutlinedButton(
                    onClick = onRegisterAthlete,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("+ Add") }
                Button(
                    onClick = {
                        vm.startSession(mode, sequencing, selected.toList(), null) { onStarted(it) }
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Start session", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Gutter)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Mode -------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Mode")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SessionMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = {
                                mode = m
                                if (m == SessionMode.INDIVIDUAL && selected.size > 1) {
                                    val keep = selected.first()
                                    selected.clear(); selected.add(keep)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, SessionMode.entries.size),
                            label = {
                                Text(
                                    if (m == SessionMode.COACH) "Coach" else "Individual",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }

            // ---- Sequencing -------------------------------------------
            if (mode == SessionMode.COACH) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Sequencing")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Sequencing.entries.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = sequencing == s,
                                onClick = { sequencing = s },
                                shape = SegmentedButtonDefaults.itemShape(i, Sequencing.entries.size),
                                label = {
                                    Text(
                                        if (s == Sequencing.TEST_WISE) "By test" else "By athlete",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        when (sequencing) {
                            Sequencing.TEST_WISE ->
                                "One test across the whole roster, then the next. " +
                                    "Station set up once per test — faster on the ground."
                            Sequencing.ATHLETE_WISE ->
                                "Each athlete completes every test before the next begins."
                        } + " Either order gives identical results; you can switch part-way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Roster -----------------------------------------------
            SectionLabel("Roster")
            if (athletes.isEmpty()) {
                AppCard {
                    Text(
                        "No athletes registered yet. Use “Add athlete” below to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                AppCard(Modifier.weight(1f)) {
                    LazyColumn {
                        items(athletes) { athlete ->
                            val checked = athlete.id in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        if (on) {
                                            if (mode == SessionMode.INDIVIDUAL) selected.clear()
                                            selected.add(athlete.id)
                                        } else selected.remove(athlete.id)
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(
                                        athlete.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        buildString {
                                            append("${athlete.ageYears} yrs · ")
                                            append(athlete.gender.name.lowercase())
                                            athlete.school?.let { append(" · $it") }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(
                                Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
