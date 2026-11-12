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
