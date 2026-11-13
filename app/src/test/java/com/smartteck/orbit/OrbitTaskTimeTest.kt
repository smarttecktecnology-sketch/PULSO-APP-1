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

    @Test
    fun expandsDailyAndWeeklyRecurringDates() {
        assertEquals(
            listOf("2026-11-12", "2026-11-13", "2026-11-14"),
            orbitRecurrenceDates("2026-11-12", "daily", 3)
        )
        assertEquals(
            listOf("2026-11-12", "2026-11-19", "2026-11-26"),
            orbitRecurrenceDates("2026-11-12", "weekly", 3)
        )
    }

    @Test
    fun monthlyRecurrenceClampsToLastDayOfShorterMonth() {
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31"),
            orbitRecurrenceDates("2026-01-31", "monthly", 3)
        )
    }

    @Test
    fun taskIdsRemainUniqueWhenSeveralTasksAreCreatedInSameMillisecond() {
        assertEquals(1_000L, nextOrbitTaskId(emptyList(), 1_000L))
        assertEquals(1_005L, nextOrbitTaskId(listOf(1_000L, 1_004L), 1_000L))
    }

    @Test
    fun conflictDetectionFindsOnlyPendingTasksAtTheRequestedTime() {
        val tasks = listOf(
            OrbitTask(1L, "Reunião", "2026-11-20", false, "10:00"),
            OrbitTask(2L, "Concluída", "2026-11-20", true, "10:00"),
            OrbitTask(3L, "Outro horário", "2026-11-20", false, "11:00"),
            OrbitTask(4L, "Outro dia", "2026-11-21", false, "10:00")
        )
        assertEquals(
            setOf("2026-11-20"),
            orbitConflictingScheduleDates(tasks, listOf("2026-11-20", "2026-11-21"), "10:00")
        )
    }

    @Test
    fun conflictDetectionCanExcludeTheTaskBeingEdited() {
        val tasks = listOf(
            OrbitTask(1L, "Reunião", "2026-11-20", false, "10:00"),
            OrbitTask(2L, "Outra", "2026-11-20", false, "10:00")
        )
        assertEquals(
            setOf("2026-11-20"),
            orbitConflictingScheduleDates(tasks, listOf("2026-11-20"), "10:00", excludingTaskId = 1L)
        )
        assertTrue(
            orbitConflictingScheduleDates(tasks.take(1), listOf("2026-11-20"), "10:00", excludingTaskId = 1L).isEmpty()
        )
    }

    @Test
    fun nonRecurringScheduleCreatesOnlyOneDate() {
        assertEquals(
            listOf("2026-11-12"),
            orbitRecurrenceDates("2026-11-12", "once", 12)
        )
    }

    @Test
    fun rejectsUnknownRecurrenceInsteadOfDuplicatingTheSameDate() {
        assertTrue(orbitRecurrenceDates("2026-11-12", "yearly").isEmpty())
        assertTrue(orbitRecurrenceDates("2026-11-12", "daily", 0).isEmpty())
    }

    @Test
    fun voiceRemindersAreAvailableInTestEditionWhenUserEnablesThem() {
        assertTrue(orbitVoiceReminderAllowed(true, OrbitPlanTier.FREE.name, true, true))
    }

    @Test
    fun voiceRemindersRequireEligiblePlanAndBothSwitchesInOfficialEdition() {
        assertFalse(orbitVoiceReminderAllowed(false, OrbitPlanTier.FREE.name, true, true))
        assertFalse(orbitVoiceReminderAllowed(false, OrbitPlanTier.PLUS.name, false, true))
        assertFalse(orbitVoiceReminderAllowed(false, OrbitPlanTier.MAX.name, true, false))
        assertTrue(orbitVoiceReminderAllowed(false, OrbitPlanTier.PLUS.name, true, true))
        assertTrue(orbitVoiceReminderAllowed(false, OrbitPlanTier.MAX.name, true, true))
    }

}
