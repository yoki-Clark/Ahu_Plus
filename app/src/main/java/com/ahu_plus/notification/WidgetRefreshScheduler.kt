package com.ahu_plus.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ahu_plus.ui.widget.TodayScheduleWidgetData
import com.ahu_plus.ui.widget.TodayScheduleWidgetUpdater
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        TodayScheduleWidgetUpdater.updateAll(applicationContext)
        CourseReminderScheduler.scheduleAll(applicationContext)
        AgendaReminderScheduler.scheduleAll(applicationContext)
        WidgetRefreshScheduler.replan(applicationContext)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}

object WidgetRefreshScheduler {
    private const val MIDNIGHT_WORK = "widget-refresh-midnight"
    private const val COURSE_BOUNDARY_WORK = "widget-refresh-course-boundary"

    suspend fun refreshAndReplan(context: Context) {
        TodayScheduleWidgetUpdater.updateAll(context.applicationContext)
        CourseReminderScheduler.scheduleAll(context.applicationContext)
        AgendaReminderScheduler.scheduleAll(context.applicationContext)
        replan(context)
    }

    suspend fun replan(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val now = System.currentTimeMillis()
        val boundary = TodayScheduleWidgetData.load(appContext).nextRefreshAtMillis
        val plan = buildPlan(now, boundary, ZoneId.systemDefault())
        enqueue(workManager, MIDNIGHT_WORK, plan.midnightDelayMillis)

        if (plan.courseBoundaryDelayMillis == null) {
            workManager.cancelUniqueWork(COURSE_BOUNDARY_WORK)
        } else {
            enqueue(workManager, COURSE_BOUNDARY_WORK, plan.courseBoundaryDelayMillis)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(MIDNIGHT_WORK)
            cancelUniqueWork(COURSE_BOUNDARY_WORK)
        }
        cancelLegacyAlarms(context)
    }

    fun cancelLegacyAlarms(context: Context) {
        WidgetUpdateScheduler.cancel(context.applicationContext)
    }

    private fun enqueue(workManager: WorkManager, name: String, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
            .addTag(name)
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun buildPlan(
        nowMillis: Long,
        nextBoundaryMillis: Long?,
        zoneId: ZoneId,
    ): WidgetRefreshPlan {
        val tomorrow = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().plusDays(1)
        val midnight = tomorrow.atStartOfDay(zoneId).plusSeconds(5).toInstant().toEpochMilli()
        return WidgetRefreshPlan(
            midnightDelayMillis = (midnight - nowMillis).coerceAtLeast(1_000L),
            courseBoundaryDelayMillis = nextBoundaryMillis
                ?.minus(nowMillis)
                ?.coerceAtLeast(1_000L),
        )
    }
}

internal data class WidgetRefreshPlan(
    val midnightDelayMillis: Long,
    val courseBoundaryDelayMillis: Long?,
)
