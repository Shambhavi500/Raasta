package com.raasta.app.ui.plan

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.planner.CheckStatus
import com.raasta.app.planner.DataCheck
import com.raasta.app.planner.Topic
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.ErrorOnDark
import com.raasta.app.ui.theme.MutedOnDark
import com.raasta.app.ui.theme.Obsidian
import com.raasta.app.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

/** Route analysis: what data Raasta gathered, how far to trust it, and what is unknown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCheckScreen(vm: TripViewModel, onBack: () -> Unit, onCompare: () -> Unit) {
    val checks = vm.dataChecks
    val options = vm.options
    var revealed by rememberSaveable { mutableIntStateOf(0) }
    val done = vm.analysisDone && revealed >= checks.size

    LaunchedEffect(options, vm.safetyStatus) { vm.analyse(options.map { it.journey }) }
    // Show each check in turn so the rider can follow what is being looked at.
    LaunchedEffect(checks.size) {
        while (revealed < checks.size) {
            delay(REVEAL_MS)
            revealed++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.check_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onCompare,
                enabled = done && options.isNotEmpty(),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
            ) {
                Text(
                    when {
                        !done -> stringResource(R.string.check_working)
                        options.isEmpty() -> stringResource(R.string.plan_no_results)
                        else -> pluralStringResource(R.plurals.check_compare, options.size, options.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.options_route, vm.fromName, vm.destinationName) + " · " + clock(vm.searchMinute),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        preferencesText(vm.preferences),
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricLime,
                    )
                }
            }
            itemsIndexed(checks, key = { _, check -> check.topic }) { index, check ->
                AnimatedVisibility(visible = index < revealed, enter = fadeIn() + slideInVertically { it / 3 }) {
                    CheckRow(check)
                }
            }
            item {
                if (!done) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ElectricLime)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(if (vm.preferences.safetyAware) R.string.check_ranking_safety else R.string.check_ranking),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            pluralStringResource(R.plurals.check_found, options.size, options.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (vm.notRunningCount > 0) {
                            Text(
                                stringResource(R.string.options_metro_closed, vm.notRunningCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AttentionAmber,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(check: DataCheck) {
    val (icon, color) = statusLook(check.status)
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = stringResource(statusLabel(check.status)), tint = color, modifier = Modifier.size(22.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(topicLabel(check.topic)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(stringResource(statusLabel(check.status)), style = MaterialTheme.typography.labelMedium, color = color)
            }
            Text(checkDetail(check), style = MaterialTheme.typography.bodySmall)
            if (check.status == CheckStatus.OUTDATED && check.ageDays != null) {
                Text(
                    stringResource(R.string.check_outdated, check.ageDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorOnDark,
                )
            }
            check.source?.let {
                Text(
                    stringResource(R.string.check_source, it.title, it.retrieved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun statusLook(status: CheckStatus): Pair<ImageVector, Color> = when (status) {
    CheckStatus.OK -> Icons.Filled.CheckCircle to SuccessGreen
    CheckStatus.ESTIMATE -> Icons.Filled.Info to AttentionAmber
    CheckStatus.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline to MutedOnDark
    CheckStatus.OUTDATED -> Icons.Filled.History to ErrorOnDark
}

@StringRes
private fun statusLabel(status: CheckStatus): Int = when (status) {
    CheckStatus.OK -> R.string.check_status_ok
    CheckStatus.ESTIMATE -> R.string.check_status_estimate
    CheckStatus.UNKNOWN -> R.string.check_status_unknown
    CheckStatus.OUTDATED -> R.string.check_status_outdated
}

@StringRes
private fun topicLabel(topic: Topic): Int = when (topic) {
    Topic.METRO_FARES -> R.string.topic_metro_fares
    Topic.BUS_FARES -> R.string.topic_bus_fares
    Topic.AUTO_FARES -> R.string.topic_auto_fares
    Topic.BUS_TIMES -> R.string.topic_bus_times
    Topic.METRO_TIMES -> R.string.topic_metro_times
    Topic.WALKING_ROUTES -> R.string.topic_walking
    Topic.LIVE_UPDATES -> R.string.topic_live
    Topic.RIDER_REPORTS -> R.string.topic_reports
    Topic.STREET_SAFETY -> R.string.topic_safety
    Topic.STEP_FREE_ACCESS -> R.string.topic_step_free
    Topic.AUTO_AVAILABILITY -> R.string.topic_auto_availability
}

@Composable
private fun checkDetail(check: DataCheck): String = when (check.topic) {
    Topic.METRO_FARES -> stringResource(R.string.detail_metro_fares)
    Topic.BUS_FARES -> stringResource(R.string.detail_bus_fares)
    Topic.AUTO_FARES -> stringResource(R.string.detail_auto_fares)
    Topic.BUS_TIMES -> stringResource(
        if (check.status == CheckStatus.ESTIMATE) R.string.detail_bus_times_estimate else R.string.detail_bus_times,
    )
    Topic.METRO_TIMES -> stringResource(R.string.detail_metro_times)
    Topic.WALKING_ROUTES -> stringResource(R.string.detail_walking)
    Topic.LIVE_UPDATES -> stringResource(R.string.detail_live)
    Topic.RIDER_REPORTS -> if (check.count == 0 && check.extra == 0) {
        stringResource(R.string.detail_reports_none)
    } else {
        stringResource(R.string.detail_reports, check.count, check.extra)
    }
    Topic.STREET_SAFETY -> stringResource(
        when (check.status) {
            CheckStatus.OK -> R.string.detail_safety
            CheckStatus.ESTIMATE -> R.string.detail_safety_demo
            else -> R.string.detail_safety_unavailable
        },
    )
    Topic.STEP_FREE_ACCESS -> stringResource(R.string.detail_step_free)
    Topic.AUTO_AVAILABILITY -> stringResource(R.string.detail_auto_availability)
}

private const val REVEAL_MS = 260L
