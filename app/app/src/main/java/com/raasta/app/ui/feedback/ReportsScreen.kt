package com.raasta.app.ui.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raasta.app.R
import com.raasta.app.store.FeedbackLog
import com.raasta.app.store.ReportStatus
import com.raasta.app.store.RouteReport
import com.raasta.app.ui.TripViewModel
import com.raasta.app.ui.plan.Tag
import com.raasta.app.ui.plan.conditionLabel
import com.raasta.app.ui.plan.displayRoute
import com.raasta.app.ui.theme.AttentionAmber
import com.raasta.app.ui.theme.ElectricLime
import com.raasta.app.ui.theme.ErrorOnDark
import com.raasta.app.ui.theme.Obsidian
import com.raasta.app.ui.theme.SuccessGreen
import java.text.DateFormat
import java.util.Date

/**
 * Reports the rider has sent and their review status. Only verified reports change route ranking.
 * In this demo there is no server, so the rider can act as the reviewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: TripViewModel, onBack: () -> Unit) {
    val log = vm.feedbackLog
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.reports_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.reports_demo_reviewer),
                        style = MaterialTheme.typography.bodySmall,
                        color = AttentionAmber,
                    )
                    Text(
                        pluralStringResource(R.plurals.reports_feedback_count, log.feedback.size, log.feedback.size) + " · " +
                            pluralStringResource(R.plurals.reports_verified_count, log.verified.size, log.verified.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (log.reports.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.reports_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(log.reports.sortedByDescending { it.createdAtMillis }, key = { it.id }) { report ->
                ReportCard(report, log, onReview = { status -> vm.review(report.id, status) })
            }
        }
    }
}

@Composable
private fun ReportCard(report: RouteReport, log: FeedbackLog, onReview: (ReportStatus) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(conditionLabel(report.condition)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val (label, color) = when (report.status) {
                    ReportStatus.UNVERIFIED -> R.string.report_unverified to AttentionAmber
                    ReportStatus.VERIFIED -> R.string.report_verified to SuccessGreen
                    ReportStatus.REJECTED -> R.string.report_rejected to ErrorOnDark
                }
                Tag(stringResource(label), color, outlined = true)
            }
            Text(
                listOfNotNull(report.place, report.route?.let { stringResource(R.string.recover_bus_route, displayRoute(it)) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                report.journey + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.createdAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (report.status) {
                ReportStatus.UNVERIFIED -> {
                    val matches = log.matches(report)
                    Text(
                        pluralStringResource(R.plurals.report_matches, matches, matches),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onReview(ReportStatus.VERIFIED) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = Obsidian),
                        ) { Text(stringResource(R.string.report_verify)) }
                        OutlinedButton(onClick = { onReview(ReportStatus.REJECTED) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.report_reject))
                        }
                    }
                }
                ReportStatus.VERIFIED -> Text(
                    stringResource(R.string.report_verified_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen,
                )
                ReportStatus.REJECTED -> Text(
                    stringResource(R.string.report_rejected_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
