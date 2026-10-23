package com.raasta.app.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raasta.app.planner.Condition
import com.raasta.app.planner.Evidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.reportsDataStore by preferencesDataStore(name = "route_reports")

/** A report starts unverified and only counts as evidence once a reviewer verifies it. */
enum class ReportStatus { UNVERIFIED, VERIFIED, REJECTED }

enum class TripRating { GOOD, OKAY, POOR }

@Serializable
data class RouteReport(
    val id: String,
    val createdAtMillis: Long,
    val condition: Condition,
    /** Stop, station or place name as it appears on journey legs. */
    val place: String,
    val route: String? = null,
    /** "From → To" of the journey it was reported on. */
    val journey: String,
    val status: ReportStatus = ReportStatus.UNVERIFIED,
) {
    fun toEvidence() = Evidence(condition, place, route)
}

@Serializable
data class JourneyFeedback(
    val id: String,
    val createdAtMillis: Long,
    val journey: String,
    val rating: TripRating,
    val reportIds: List<String>,
)

@Serializable
data class FeedbackLog(
    val reports: List<RouteReport> = emptyList(),
    val feedback: List<JourneyFeedback> = emptyList(),
) {
    val verified: List<RouteReport> get() = reports.filter { it.status == ReportStatus.VERIFIED }
    val pending: List<RouteReport> get() = reports.filter { it.status == ReportStatus.UNVERIFIED }

    /** Other reports of the same condition at the same place, to help a reviewer decide. */
    fun matches(report: RouteReport): Int =
        reports.count { it.id != report.id && it.condition == report.condition && it.place == report.place && it.route == report.route }
}

/**
 * Journey feedback and route reports, kept on the device for review (demo: there is no server).
 * Only reports a reviewer has verified are used when ranking routes.
 */
class ReportStore(private val context: Context) {
    private val key = stringPreferencesKey("log")
    private val json = Json { ignoreUnknownKeys = true }

    val log: Flow<FeedbackLog> = context.reportsDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString(FeedbackLog.serializer(), it) }.getOrNull() } ?: FeedbackLog()
    }

    suspend fun addFeedback(feedback: JourneyFeedback, reports: List<RouteReport>) = update {
        it.copy(reports = it.reports + reports, feedback = it.feedback + feedback)
    }

    suspend fun setStatus(reportId: String, status: ReportStatus) = update { log ->
        log.copy(reports = log.reports.map { if (it.id == reportId) it.copy(status = status) else it })
    }

    private suspend fun update(change: (FeedbackLog) -> FeedbackLog) {
        context.reportsDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString(FeedbackLog.serializer(), it) }.getOrNull() } ?: FeedbackLog()
            prefs[key] = json.encodeToString(FeedbackLog.serializer(), change(current))
        }
    }
}
