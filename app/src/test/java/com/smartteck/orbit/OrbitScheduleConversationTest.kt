package com.smartteck.orbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class OrbitScheduleConversationTest {
    @Test
    fun asksForTimeAndCombinesTheNextMessageWithOriginalRequest() {
        val conversation = OrbitScheduleConversation()
        val first = conversation.submit("Me lembre de estudar amanhã")
        assertEquals("por favor defina o horário do lembrete", first.message)
        assertNull(first.draft)
        assertTrue(conversation.hasPendingRequest())

        val second = conversation.submit("às 19h")
        assertNotNull(second.draft)
        assertEquals("Estudar", second.draft?.title)
        assertEquals("19:00", second.draft?.time)
        assertFalse(conversation.hasPendingRequest())
    }

    @Test
    fun newCompleteRequestReplacesPendingRequest() {
        val conversation = OrbitScheduleConversation()
        conversation.submit("Me lembre de estudar amanhã")
        val result = conversation.submit("Consulta sexta-feira às 10h")
        assertNotNull(result.draft)
        assertEquals("Consulta", result.draft?.title)
        assertEquals("10:00", result.draft?.time)
    }

    @Test
    fun clearsPendingRequest() {
        val conversation = OrbitScheduleConversation()
        conversation.submit("Me lembre de estudar amanhã")
        conversation.clear()
        assertFalse(conversation.hasPendingRequest())
        assertNull(conversation.submit("às 19h").draft)
    }

    @Test
    fun localDateTimeKeepsTheRequestedCalendarDay() {
        val millis = orbitLocalDateTimeMillis("2027-05-18", "19:45")
        assertNotNull(millis)
        val calendar = Calendar.getInstance().apply { timeInMillis = millis!! }
        assertEquals(2027, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, calendar.get(Calendar.MONTH))
        assertEquals(18, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun rejectsImpossibleLocalDateAndTime() {
        assertNull(orbitLocalDateTimeMillis("2027-02-31", "10:00"))
        assertNull(orbitLocalDateTimeMillis("2027-05-18", "25:00"))
    }
}
