package com.smartteck.orbit

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared date/time helpers for agenda ordering and overdue labels.
 * All comparisons use the device's local timezone, matching reminder scheduling.
 */
internal object OrbitTaskTime {
    fun dueMillis(task: OrbitTask): Long? {
        if (task.dueDate.isBlank()) return null
        return orbitLocalDateTimeMillis(task.dueDate, task.reminderTime)
    }

    fun isOverdue(task: OrbitTask, nowMillis: Long = System.currentTimeMillis()): Boolean =
        !task.done && (dueMillis(task)?.let { it < nowMillis } == true)

    fun sortByDueDate(tasks: List<OrbitTask>): List<OrbitTask> =
        tasks.sortedWith(
            compareBy<OrbitTask>({ dueMillis(it) ?: Long.MAX_VALUE }, { it.id })
        )

    fun displayDate(value: String): String {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = parser.parse(value, position)
        if (parsed == null || position.index != value.length) return value
        return SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(parsed)
    }
}


/**
 * Expands a recurring agenda item into up to twelve individual dated reminders.
 * Dates are stored in the same yyyy-MM-dd format used by the task repository.
 */
internal fun orbitRecurrenceDates(date: String, recurrence: String, count: Int = 12): List<String> {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val parsed = parser.parse(date, position) ?: return emptyList()
    if (position.index != date.length) return emptyList()
    val base = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), Locale.US).apply {
        time = parsed
    }
    val originalDay = base.get(java.util.Calendar.DAY_OF_MONTH)
    val total = if (recurrence == "once") 1 else count.coerceIn(1, 12)
    return (0 until total).map { index ->
        val occurrence = (base.clone() as java.util.Calendar)
        when (recurrence) {
            "daily" -> occurrence.add(java.util.Calendar.DAY_OF_YEAR, index)
            "weekly" -> occurrence.add(java.util.Calendar.WEEK_OF_YEAR, index)
            "monthly" -> {
                occurrence.set(java.util.Calendar.DAY_OF_MONTH, 1)
                occurrence.add(java.util.Calendar.MONTH, index)
                val lastDay = occurrence.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                occurrence.set(java.util.Calendar.DAY_OF_MONTH, minOf(originalDay, lastDay))
            }
        }
        parser.format(occurrence.time)
    }
}
