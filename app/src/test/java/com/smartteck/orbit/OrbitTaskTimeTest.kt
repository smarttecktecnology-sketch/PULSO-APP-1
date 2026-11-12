package com.smartteck.orbit

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitTaskTimeTest {
    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    @Test
    fun marksEarlierTimeTodayAsOverdue() {
        val task = OrbitTask(1L, "Estudar", today(), false, "08:00")
        val due = OrbitTaskTime.dueMillis(task) ?: error("Data inválida")
        assertTrue(OrbitTaskTime.isOverdue(task, due + 1L))
    }

    @Test
    fun doesNotMarkFutureTaskAsOverdue() {
        val task = OrbitTask(2L, "Ler", today(), false, "23:59")
        val due = OrbitTaskTime.dueMillis(task) ?: error("Data inválida")
        assertFalse(OrbitTaskTime.isOverdue(task, due - 1L))
    }

    @Test
    fun completedTaskIsNeverOverdue() {
        val task = OrbitTask(3L, "Concluída", today(), true, "08:00")
        val due = OrbitTaskTime.dueMillis(task) ?: error("Data inválida")
        assertFalse(OrbitTaskTime.isOverdue(task, due + 1L))
    }

    @Test
    fun agendaSortsByDateAndTimeAndPlacesUndatedLast() {
        val date = today()
        val later = OrbitTask(4L, "Mais tarde", date, false, "18:00")
        val earlier = OrbitTask(5L, "Mais cedo", date, false, "09:00")
        val undated = OrbitTask(6L, "Sem data", "", false, "09:00")
        assertEquals(
            listOf(earlier.id, later.id, undated.id),
            OrbitTaskTime.sortByDueDate(listOf(undated, later, earlier)).map { it.id }
        )
    }

    @Test
    fun displaysDateInBrazilianFormat() {
        assertEquals("12/11/2026", OrbitTaskTime.displayDate("2026-11-12"))
    }
}
