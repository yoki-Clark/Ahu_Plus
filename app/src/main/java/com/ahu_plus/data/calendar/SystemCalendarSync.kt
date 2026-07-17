package com.ahu_plus.data.calendar

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.ahu_plus.data.model.agenda.AgendaEvent
import com.ahu_plus.data.model.agenda.AgendaSource
import java.time.ZoneId

data class CalendarSyncResult(
    val calendarName: String,
    val inserted: Int,
    val updated: Int,
    val removed: Int,
)

class SystemCalendarSync(private val context: Context) {
    private val resolver = context.contentResolver
    private val zone = ZoneId.systemDefault()

    fun sync(events: List<AgendaEvent>): CalendarSyncResult {
        val calendar = findWritableCalendar()
            ?: error("未找到可写入的系统日历")
        val managedEvents = readManagedEvents(calendar.id)
        val desired = events
            .filter { it.source == AgendaSource.COURSE || it.source == AgendaSource.EXAM }
            .distinctBy { it.id }
            .associateBy { it.id }

        var inserted = 0
        var updated = 0
        var removed = 0
        val operations = ArrayList<ContentProviderOperation>()

        desired.forEach { (managedId, event) ->
            val existingIds = managedEvents[managedId].orEmpty()
            val values = eventValues(calendar.id, event)
            val existingId = existingIds.firstOrNull()
            if (existingId == null) {
                operations += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValues(values)
                    .build()
                inserted++
            } else {
                operations += ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                ).withValues(values).build()
                updated++
                existingIds.drop(1).forEach { duplicateId ->
                    operations += ContentProviderOperation.newDelete(
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, duplicateId)
                    ).build()
                    removed++
                }
            }
        }

        managedEvents
            .filterKeys { it !in desired }
            .values
            .flatten()
            .forEach { staleId ->
                operations += ContentProviderOperation.newDelete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, staleId)
                ).build()
                removed++
            }

        operations.chunked(BATCH_SIZE).forEach { batch ->
            resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(batch))
        }
        return CalendarSyncResult(calendar.name, inserted, updated, removed)
    }

    private fun findWritableCalendar(): WritableCalendar? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        return resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor ->
            val candidates = mutableListOf<Pair<Int, WritableCalendar>>()
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val visibleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
            val primaryIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            while (cursor.moveToNext()) {
                val visible = cursor.getInt(visibleIndex) == 1
                val primary = !cursor.isNull(primaryIndex) && cursor.getInt(primaryIndex) == 1
                val score = (if (primary) 2 else 0) + (if (visible) 1 else 0)
                candidates += score to WritableCalendar(
                    id = cursor.getLong(idIndex),
                    name = cursor.getString(nameIndex)?.ifBlank { "系统日历" } ?: "系统日历",
                )
            }
            candidates.maxByOrNull { it.first }?.second
        }
    }

    private fun readManagedEvents(calendarId: Long): Map<String, List<Long>> {
        val result = linkedMapOf<String, MutableList<Long>>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf(calendarId.toString(), "%$MARKER_PREFIX%"),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            while (cursor.moveToNext()) {
                val description = cursor.getString(descriptionIndex).orEmpty()
                val managedId = MARKER_REGEX.find(description)?.groupValues?.getOrNull(1) ?: continue
                result.getOrPut(managedId) { mutableListOf() } += cursor.getLong(idIndex)
            }
        }
        return result
    }

    private fun eventValues(calendarId: Long, event: AgendaEvent): ContentValues {
        val allDay = event.startMinutes == null
        val start = if (allDay) {
            event.date.atStartOfDay(zone)
        } else {
            event.date.atStartOfDay(zone).plusMinutes(event.startMinutes!!.toLong())
        }
        val end = if (allDay) {
            event.date.plusDays(1).atStartOfDay(zone)
        } else {
            event.date.atStartOfDay(zone).plusMinutes(
                (event.endMinutes ?: (event.startMinutes + DEFAULT_DURATION_MINUTES)).toLong()
            )
        }
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.EVENT_LOCATION, event.location.orEmpty())
            put(CalendarContract.Events.DESCRIPTION, "${sourceLabel(event.source)}\n$MARKER_PREFIX${event.id}]")
            put(CalendarContract.Events.DTSTART, start.toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, end.toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
    }

    private fun sourceLabel(source: AgendaSource): String = when (source) {
        AgendaSource.COURSE -> "Ahu Plus 课程"
        AgendaSource.EXAM -> "Ahu Plus 考试"
        else -> "Ahu Plus 日程"
    }

    private data class WritableCalendar(val id: Long, val name: String)

    private companion object {
        const val BATCH_SIZE = 100
        const val DEFAULT_DURATION_MINUTES = 60
        const val MARKER_PREFIX = "[Ahu Plus:"
        val MARKER_REGEX = Regex("\\[Ahu Plus:(.+)]")
    }
}
