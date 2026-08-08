package com.batteryfitness.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batteryfitness.app.data.Sequencing
import com.batteryfitness.app.domain.BatteryTest
import com.batteryfitness.app.domain.Cell
import com.batteryfitness.app.domain.Measurement
import com.batteryfitness.app.ui.AppViewModel
import com.batteryfitness.app.ui.components.AppCard
import com.batteryfitness.app.ui.components.AppScaffold
import com.batteryfitness.app.ui.components.Gutter
import com.batteryfitness.app.ui.components.SectionLabel
import com.batteryfitness.app.ui.components.StatusPill

/**
 * The working screen during a session.
 *
 * It renders whichever axis the assessor is working along — stations in
 * test-wise mode, athletes in athlete-wise — but both read the same completion
 * set. That is the visible consequence of sequencing being only a traversal
 * order (§6.1), and switching mid-session is exposed deliberately because
 * nothing already recorded is re-requested.
 */
@Composable
fun SessionScreen(
    vm: AppViewModel,
    onOpenCell: (athleteId: Long, test: BatteryTest) -> Unit,
    onBack: () -> Unit,
) {
    val s by vm.session.collectAsState()

    AppScaffold(
        title = "Session ${s.sessionId}",
        subtitle = "${s.progress} recorded · ${s.roster.size} athlete" +
            if (s.roster.size == 1) "" else "s",
        onBack = onBack,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Gutter)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Progress ---------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { s.progress.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${s.progress.done} of ${s.progress.total} recorded",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${(s.progress.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ---- Next up ----------------------------------------------
            s.nextCell?.let { next ->
                val who = s.athlete(next.athleteId)?.name ?: "athlete"
                Card(
                    onClick = { onOpenCell(next.athleteId, next.test) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "NEXT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${next.test.number}. ${next.test.label()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                who,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            )
                        }
                        Text(
                            "›",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            } ?: AppCard {
                Text(
                    "Battery complete for every athlete on this roster.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ---- View axis --------------------------------------------
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Sequencing.entries.forEachIndexed { i, seq ->
                    SegmentedButton(
                        selected = s.sequencing == seq,
                        onClick = { vm.setSequencing(seq) },
                        shape = SegmentedButtonDefaults.itemShape(i, Sequencing.entries.size),
                        label = {
                            Text(
                                if (seq == Sequencing.TEST_WISE) "By test" else "By athlete",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            SectionLabel(if (s.sequencing == Sequencing.TEST_WISE) "Stations" else "Athletes")

            AppCard(Modifier.weight(1f)) {
                LazyColumn {
                    when (s.sequencing) {
                        Sequencing.TEST_WISE -> {
                            val perTest = s.perTest
                            items(s.tests) { test ->
                                val p = perTest[test]
                                ListRow(
                                    lead = "%02d".format(test.number),
                                    title = test.label(),
                                    subtitle = buildString {
                                        append(
                                            when (test.measurement) {
                                                Measurement.AI -> "AI measured"
                                                Measurement.AI_PLANNED -> "Manual · AI planned"
                                                Measurement.OCR_ASSIST -> "Manual · OCR assist"
                                                Measurement.MANUAL -> "Manual entry"
                                            }
                                        )
                                        append(" · ")
                                        append(
                                            if (test.isPhoto) "photo"
                                            else "${test.minDurationSec}–${test.maxDurationSec}s"
                                        )
                                    },
                                    trailing = p?.toString() ?: "",
                                    done = p?.complete == true,
                                    onClick = {
                                        val target = s.roster.firstOrNull {
                                            Cell(it.id, test) !in s.completed
                                        } ?: s.roster.firstOrNull()
                                        target?.let { onOpenCell(it.id, test) }
                                    },
                                )
                                HorizontalDivider(
                                    Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }

                        Sequencing.ATHLETE_WISE -> {
                            val perAthlete = s.perAthlete
                            items(s.roster) { athlete ->
                                val p = perAthlete[athlete.id]
                                ListRow(
                                    lead = athlete.name.take(1).uppercase(),
                                    title = athlete.name,
                                    subtitle = "${athlete.ageYears} yrs · " +
                                        athlete.gender.name.lowercase(),
                                    trailing = p?.toString() ?: "",
                                    done = p?.complete == true,
                                    onClick = {
                                        val test = s.tests.firstOrNull {
                                            Cell(athlete.id, it) !in s.completed
                                        } ?: s.tests.first()
                                        onOpenCell(athlete.id, test)
                                    },
                                )
                                HorizontalDivider(
                                    Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ListRow(
    lead: String,
    title: String,
    subtitle: String,
    trailing: String,
    done: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            lead,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(
            text = if (done) "✓ $trailing" else trailing,
            container = if (done) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (done) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
