package com.smartteck.orbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Test
    fun parsesInformalReminderAndCleansTitle() {
        val draft = OrbitCommandParser.parseSchedule("Me lembra de estudar amanhã às 19h")
        requireNotNull(draft)
        assertEquals("Estudar", draft.title)
        assertEquals(dateAfter(1), draft.date)
        assertEquals("19:00", draft.time)
        assertEquals(0, draft.reminderLeadMinutes)
    }

    @Test
    fun parsesOneHourReminderLead() {
        val draft = OrbitCommandParser.parseSchedule("Agende consulta amanhã às 10h e me avise 1 hora antes")
        requireNotNull(draft)
        assertEquals("Consulta", draft.title)
        assertEquals(60, draft.reminderLeadMinutes)
    }

    @Test
    fun requiresDateAndTimeToCreateSchedule() {
        assertNull(OrbitCommandParser.parseSchedule("Agende estudar"))
        assertNull(OrbitCommandParser.parseSchedule("Agende estudar amanhã"))
    }

    @Test
    fun recognizesSchedulingKeywordsInsideNaturalSentences() {
        val draft = OrbitCommandParser.parseSchedule("Não posso esquecer de estudar amanhã às 08:30")
        requireNotNull(draft)
        assertEquals("Estudar", draft.title)
        assertEquals(dateAfter(1), draft.date)
        assertEquals("08:30", draft.time)
    }

    @Test
    fun recognizesReminderIntentWithDifferentWording() {
        val draft = OrbitCommandParser.parseSchedule("Preciso lembrar de levar o documento sexta às 14h")
        requireNotNull(draft)
        assertEquals("Levar o documento", draft.title)
        assertEquals("14:00", draft.time)
    }

    @Test
    fun detectsIntentEvenWhenItIsNotAtTheBeginning() {
        assertEquals(true, OrbitCommandParser.hasSchedulingIntent("Amanhã preciso que você me avise às 9h para estudar"))
    }

    @Test
    fun recognizesManyPortugueseSchedulingSynonyms() {
        val phrases = listOf(
            "Adicione estudar amanhã às 18h",
            "Inclua reunião amanhã às 10h",
            "Registre consulta amanhã às 09h",
            "Anote prova amanhã às 08h",
            "Programe aula amanhã às 14h",
            "Me alerte sobre a entrega amanhã às 16h",
            "Tenho compromisso amanhã às 11h"
        )
        phrases.forEach { phrase ->
            assertEquals("Should recognize: $phrase", true,
                OrbitCommandParser.hasSchedulingIntent(phrase))
        }
    }

    @Test
    fun cleansAccentedDoNotForgetPhrase() {
        val draft = OrbitCommandParser.parseSchedule(
            "Não posso esquecer de estudar amanhã às 08:30"
        )
        requireNotNull(draft)
        assertEquals("Estudar", draft.title)
        assertEquals(dateAfter(1), draft.date)
        assertEquals("08:30", draft.time)
    }


    @Test
    fun requiresExplicitTimeEvenWhenDateAndIntentExist() {
        assertNull(OrbitCommandParser.parseSchedule("Me lembre de estudar amanhã"))
    }

    @Test
    fun parsesAppointmentWithExplicitClockTimeAndLeadMinutes() {
        val draft = OrbitCommandParser.parseSchedule(
            "Tenho compromisso amanhã às 16:45 com aviso 20 minutos antes"
        )
        requireNotNull(draft)
        assertEquals("Compromisso", draft.title)
        assertEquals("16:45", draft.time)
        assertEquals(20, draft.reminderLeadMinutes)
    }

    @Test
    fun removesDifferentCommandPrefixesFromAppointmentTitle() {
        val phrases = listOf(
            "Adicione estudar amanhã às 18h" to "Estudar",
            "Inclua reunião amanhã às 10h" to "Reunião",
            "Anote prova amanhã às 08h" to "Prova",
            "Programe aula amanhã às 14h" to "Aula"
        )
        phrases.forEach { (phrase, expectedTitle) ->
            val draft = OrbitCommandParser.parseSchedule(phrase)
            requireNotNull(draft)
            assertEquals(expectedTitle, draft.title)
        }
    }


    @Test
    fun recognizesAppointmentNameWithoutAnExplicitCommandVerb() {
        val draft = OrbitCommandParser.parseSchedule("Reunião amanhã às 15:30")
        requireNotNull(draft)
        assertEquals("Reunião", draft.title)
        assertEquals(dateAfter(1), draft.date)
        assertEquals("15:30", draft.time)
    }

    @Test
    fun extractsTimeFromPartialRequestsIncludingAfternoonSuffix() {
        assertEquals("15:30", OrbitCommandParser.parseExplicitTime("consulta às 3:30 da tarde"))
        assertEquals("08:00", OrbitCommandParser.parseExplicitTime("prova às 8h"))
        assertEquals("15:00", OrbitCommandParser.parseExplicitTime("consulta às 3 da tarde"))
        assertEquals(null, OrbitCommandParser.parseExplicitTime("consulta amanhã"))
    }

    @Test
    fun extractsDateFromPartialRequestsWithoutRequiringTime() {
        assertTrue(OrbitCommandParser.parseExplicitDate("dentista amanhã") != null)
        assertEquals(null, OrbitCommandParser.parseExplicitDate("dentista sem data"))
    }

}
