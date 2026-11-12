package com.smartteck.orbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OrbitCommandParserTest {
    private fun dateAfter(days: Int): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.time
        )

    @Test
    fun parsesTomorrowMeetingAndReminderLead() {
        val draft = OrbitCommandParser.parseSchedule(
            "Agende reunião amanhã às 15h e me lembrar 30 minutos antes"
        )
        requireNotNull(draft)
        assertEquals("Reunião", draft.title)
        assertEquals(dateAfter(1), draft.date)
        assertEquals("15:00", draft.time)
        assertEquals(30, draft.reminderLeadMinutes)
    }

    @Test
    fun parsesReminderPhraseAsTitleWithoutCommandNoise() {
        val draft = OrbitCommandParser.parseSchedule("Me lembra de estudar amanhã às 19h")
        requireNotNull(draft)
        assertEquals("Estudar", draft.title)
        assertEquals("19:00", draft.time)
    }

    @Test
    fun parsesWeekdayWithHyphenAndMinuteTime() {
        val draft = OrbitCommandParser.parseSchedule("Agende aula terça-feira às 08:30")
        requireNotNull(draft)
        assertEquals("Aula", draft.title)
        assertEquals("08:30", draft.time)
        val cal = Calendar.getInstance()
        var delta = (Calendar.TUESDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (delta == 0) delta = 7
        assertEquals(dateAfter(delta), draft.date)
    }

    @Test
    fun rejectsImpossibleDate() {
        assertNull(OrbitCommandParser.parseSchedule("Agende consulta 31/02/2027 às 10h"))
    }

    @Test
    fun rejectsInvalidTime() {
        assertNull(OrbitCommandParser.parseSchedule("Agende consulta amanhã às 25h"))
    }
}
