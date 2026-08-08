package com.batteryfitness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryfitness.app.ui.theme.AppBackground
import com.batteryfitness.app.ui.theme.IndiaGreen
import com.batteryfitness.app.ui.theme.Saffron

/**
 * Shared page chrome.
 *
 * Every screen goes through here so the app reads as one system rather than a
 * set of separately-built pages: same bar height, same back affordance, same
 * title treatment, same content gutter.
 *
 * The design register is deliberately institutional rather than consumer —
 * dense, labelled, restrained. This is a government assessment tool used by PE
 * teachers, and Concept Note §10 requires it to stay readable "in low light and
 * direct sunlight", which rules out low-contrast decoration.
 */

/** Standard content gutter. Everything aligns to this. */
val Gutter = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                subtitle?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            onBack?.let {
                                IconButton(onClick = it) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            }
                        },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                    TricolourRule()
                }
            },
            bottomBar = bottomBar,
            content = content,
        )
    }
}

/**
 * A 3 px national tricolour rule under the app bar.
 *
 * Sits below the bar rather than at the screen edge, where the status bar was
 * clipping it. Thin enough to read as institutional trim rather than decoration.
 */
@Composable
fun TricolourRule(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(3.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().height(3.dp)) {
            Surface(color = Saffron, content = {}, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
        Box(Modifier.weight(1f)) {
            Surface(
                color = MaterialTheme.colorScheme.outlineVariant,
                content = {},
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            Surface(color = IndiaGreen, content = {}, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

/** Section label. One style, used everywhere a group of content starts. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
    )
}

/** The standard content card. Slightly translucent so the motif shows through. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = { content() },
    )
}

/** Label-on-the-left, value-on-the-right row. Used for every spec listing. */
@Composable
fun KeyValueRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
            color = if (emphasis) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Small status pill — AI / AI planned / Manual, confidence bands, counts. */
@Composable
fun StatusPill(
    text: String,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = container,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Bottom action bar.
 *
 * Primary actions live here rather than at the end of a scroll, so they are
 * always reachable with one thumb — the operator is standing on a field holding
 * the phone one-handed, not sitting down reading.
 */
@Composable
fun BottomActionBar(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = color,
        content = {},
        modifier = Modifier.size(size.dp),
    )
}

@Composable
fun HGap(width: Int) = Spacer(Modifier.width(width.dp))

@Composable
private fun Spacer(modifier: Modifier) = Box(modifier)
