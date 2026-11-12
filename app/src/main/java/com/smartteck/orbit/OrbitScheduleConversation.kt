package com.smartteck.orbit

import java.util.Calendar
import java.util.Locale

data class OrbitAssistantReply(
    val message: String,
    val draft: OrbitScheduleDraft? = null
)

/**
 * Keeps the original request while the assistant asks for a missing time.
 * A follow-up such as "às 19h" is combined with the pending request.
 */
class OrbitScheduleConversation {
    private var pendingCommand: String? = null

    fun submit(message: String): OrbitAssistantReply {
        val input = message.trim()
        if (input.isBlank()) return OrbitAssistantReply("Descreva o compromisso.")

        val original = pendingCommand
        val isIndependentCompleteRequest =
            OrbitCommandParser.hasExplicitDate(input) && OrbitCommandParser.hasExplicitTime(input)
        // Respostas curtas (data, horário ou detalhes) complementam o pedido
        // incompleto. Uma nova solicitação substitui o rascunho anterior.
        val isNewRequest = OrbitCommandParser.hasSchedulingIntent(input) || isIndependentCompleteRequest
        val combined = if (original != null && !isNewRequest) "$original $input" else input

        val draft = OrbitCommandParser.parseSchedule(combined)
        if (draft != null) {
            pendingCommand = null
            return OrbitAssistantReply(
                "Encontrei os dados do compromisso. Confira o resumo abaixo e confirme quando estiver tudo certo.",
                draft
            )
        }

        val belongsToSchedulingFlow = original != null ||
            OrbitCommandParser.hasSchedulingIntent(input) || isIndependentCompleteRequest
        if (belongsToSchedulingFlow) {
            pendingCommand = combined
            return when {
                !OrbitCommandParser.hasExplicitTime(combined) ->
                    OrbitAssistantReply("por favor defina o horário do lembrete")
                !OrbitCommandParser.hasExplicitDate(combined) ->
                    OrbitAssistantReply("Qual é a data do compromisso?")
                else ->
                    OrbitAssistantReply("Não consegui interpretar todos os dados. Informe novamente o compromisso, a data e o horário.")
            }
        }

        return OrbitAssistantReply(
            "Eu cuido somente dos seus compromissos e lembretes. Descreva o que deseja lembrar, com data e horário."
        )
    }

    fun clear() {
        pendingCommand = null
    }

    fun hasPendingRequest(): Boolean = pendingCommand != null
}

/** Builds a local-device date/time without UTC parsing shifts. */
fun orbitLocalDateTimeMillis(date: String, time: String): Long? {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) ||
        !Regex("^\\d{1,2}:\\d{2}$").matches(time)) return null
    val dateParts = date.split("-").mapNotNull { it.toIntOrNull() }
    val timeParts = time.split(":").mapNotNull { it.toIntOrNull() }
    if (dateParts.size != 3 || timeParts.size != 2) return null
    val (year, month, day) = dateParts
    val (hour, minute) = timeParts
    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return null
    return try {
        Calendar.getInstance().apply {
            isLenient = false
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }.timeInMillis
    } catch (_: Exception) {
        null
    }
}
