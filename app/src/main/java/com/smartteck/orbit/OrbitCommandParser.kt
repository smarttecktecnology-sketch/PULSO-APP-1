package com.smartteck.orbit

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class OrbitScheduleDraft(
    val title: String,
    val date: String,
    val time: String,
    val reminderLeadMinutes: Int
)

/**
 * Offline Portuguese command parser for creating dated agenda items.
 * It deliberately asks for confirmation in the assistant UI before saving.
 */
object OrbitCommandParser {
    private val pt = Locale("pt", "BR")

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(pt), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s*-\\s*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // Vocabulário amplo de intenção: comandos diretos, formas educadas e
    // expressões comuns usadas para registrar compromissos e pedir alertas.
    private val schedulingKeywords = listOf(
        "agende", "agendar", "agenda", "agendamento", "marque", "marcar",
        "crie", "criar", "adicione", "adicionar", "inclua", "incluir",
        "registre", "registrar", "anote", "anotar", "salve", "salvar",
        "programe", "programar", "planeje", "planejar", "organize", "organizar",
        "reserve", "reservar", "lembre", "lembrar", "lembrete", "recorde",
        "recordar", "me lembra", "me lembre", "me lembrar", "lembre me",
        "lembra me", "nao posso esquecer", "não posso esquecer",
        "nao esquecer", "não esquecer", "preciso lembrar", "quero lembrar",
        "nao deixe eu esquecer", "não deixe eu esquecer", "tenho que lembrar",
        "preciso agendar", "preciso marcar", "quero agendar", "quero marcar",
        "me avise", "me avisar", "avise", "avisar", "me notifique",
        "notifique", "notificar", "notificacao", "notificação",
        "me alerte", "alertar", "alerta", "me informe", "me recorde",
        "tenho compromisso", "tenho uma consulta", "tenho consulta",
        "tenho uma reuniao", "tenho reunião", "tenho reuniao", "tenho uma prova", "tenho prova",
        "tenho uma aula", "tenho aula", "tenho um evento", "tenho evento",
        "compromisso", "reuniao", "reunião", "consulta", "prova", "aula", "evento",
        "dentista", "medico", "médico", "medica", "médica", "entrega", "entrevista",
        "treino", "estudar", "pagamento",
        "nao posso perder", "não posso perder", "nao posso me atrasar",
        "não posso me atrasar", "coloque na agenda", "coloca na agenda",
        "adicionar na agenda", "colocar na agenda"
    )

    fun hasSchedulingIntent(command: String): Boolean {
        val normalized = normalize(command)
        return schedulingKeywords.any { normalized.contains(it) }
    }

    private val timeWithUnit = Regex("\\b(?:as?\\s*)?(\\d{1,2})(?::(\\d{2})|h(\\d{2})?|\\s*horas?)\\b")
    private val hourAfterAs = Regex("\\bas\\s*(\\d{1,2})\\b")

    fun hasExplicitTime(command: String): Boolean {
        val normalized = normalize(command)
        return timeWithUnit.containsMatchIn(normalized) || hourAfterAs.containsMatchIn(normalized)
    }

    fun hasExplicitDate(command: String): Boolean =
        parseDate(normalize(command)) != null

    fun parseSchedule(command: String): OrbitScheduleDraft? {
        val normalized = normalize(command)
        // Também aceita descrições naturais sem verbo de comando, como
        // "dentista amanhã às 14h" ou "prova sexta às 8h".
        if (!hasSchedulingIntent(command) &&
            !(hasExplicitDate(command) && hasExplicitTime(command))) return null

        val date = parseDate(normalized) ?: return null
        val timeMatch = timeWithUnit.find(normalized) ?: hourAfterAs.find(normalized) ?: return null
        var hour = (timeMatch.groups[1]?.value ?: "").toIntOrNull() ?: return null
        val minute = (timeMatch.groups[2]?.value ?: timeMatch.groups[3]?.value ?: "0").toIntOrNull() ?: return null
        val timeSuffix = normalized.substring(timeMatch.range.last + 1).trimStart()
        when {
            timeSuffix.startsWith("da noite") || timeSuffix.startsWith("da tarde") -> if (hour in 1..11) hour += 12
            timeSuffix.startsWith("da manha") && hour == 12 -> hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null

        val minuteLead = Regex("(\\d{1,4})\\s*(minuto|minutos|min)\\s*antes").find(normalized)
            ?.groupValues?.get(1)?.toIntOrNull()
        val hourLead = Regex("(\\d{1,2})\\s*horas?\\s*antes").find(normalized)
            ?.groupValues?.get(1)?.toIntOrNull()
        val lead = when {
            minuteLead != null -> minuteLead.coerceIn(0, 1440)
            hourLead != null -> (hourLead * 60).coerceIn(0, 1440)
            else -> 0
        }

        var title = command.trim()
        title = title.replace(
            Regex("(?i)^\\s*(por favor[, ]*)?(agende|agendar|agenda|agendamento|marque|marcar|crie|criar|adicione|adicionar|inclua|incluir|registre|registrar|anote|anotar|salve|salvar|programe|programar|planeje|planejar|organize|organizar|reserve|reservar)\\s+"),
            ""
        )
        title = title.replace(
            Regex("(?i)^\\s*(tenho compromisso|tenho uma consulta|tenho consulta|tenho uma reuniao|tenho reuniao|tenho uma prova|tenho prova|tenho uma aula|tenho aula|tenho um evento|tenho evento|me avise|me avisar|avise|avisar|me notifique|notifique|me alerte|alerta|me informe|me recorde|me lembre|me lembrar|me lembra(r)?|lembre\\s*me|lembra\\s*me|lembrete|preciso\\s+lembrar|quero\\s+lembrar|nao\\s+posso\\s+esquecer|não\\s+posso\\s+esquecer|nao\\s+esquecer|não\\s+esquecer|nao\\s+deixe\\s+eu\\s+esquecer|não\\s+deixe\\s+eu\\s+esquecer)\\s+(de\\s+)?"),
            ""
        )
        title = title.replace(Regex("(?i)\\bdepois\\s+de\\s+amanh[ãa]\\b"), "")
        title = title.replace(Regex("(?i)\\bdaqui\\s+a\\s+\\d{1,3}\\s+dias?\\b"), "")
        title = title.replace(Regex("(?i)\\b(?:dia\\s+)?\\d{1,2}\\s+de\\s+(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)(?:\\s+de\\s+\\d{4})?\\b"), "")
        title = title.replace(Regex("(?i)\\bdia\\s+\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b"), "")
        title = title.replace(Regex("(?i)\\b(hoje|amanh[ãa]|segunda(-feira)?|ter[cç]a(-feira)?|quarta(-feira)?|quinta(-feira)?|sexta(-feira)?|s[áa]bado|domingo)\\b"), "")
        title = title.replace(Regex("\\b\\d{1,2}/\\d{1,2}(/\\d{2,4})?\\b"), "")
        title = title.replace(Regex("(?i)\\b(?:[àa]s?|para\\s+as?)\\s*\\d{1,2}(?::\\d{2}|h\\d{0,2}|\\s*horas?)\\b(?:\\s+da\\s+(?:manha|tarde|noite))?"), "")
        title = title.replace(Regex("(?i)\\b\\d{1,2}h\\d{0,2}\\b"), "")
        title = title.replace(Regex("(?i)(e\\s+)?(me\\s+)?(lembre|lembrar|lembra|avise|avisar|notifique|notificar)[^,.;]*"), "")
        title = title.replace(Regex("(?i)\\b(com\\s+)?(aviso|lembrete)\\s+\\d{1,3}\\s*(minutos?|mins?)\\s+antes\\b"), "")
        title = title.replace(Regex("(?i)\\b\\d{1,3}\\s*(minutos?|mins?)\\s+antes\\b"), "")
        title = title.replace(Regex("[,.;:!?]+"), " ").replace(Regex("\\s+"), " ").trim()
        title = title.trim('-', ' ')
        if (title.isBlank()) title = "Compromisso"
        val dueDate = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date)
        } catch (_: Exception) { null } ?: return null
        val due = Calendar.getInstance().apply {
            time = dueDate
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!due.after(Calendar.getInstance())) return null

        return OrbitScheduleDraft(title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(pt) else it.toString() },
            date, "%02d:%02d".format(Locale.US, hour, minute), lead)
    }

    private fun parseDate(normalized: String): String? {
        val cal = Calendar.getInstance()
        when {
            normalized.contains("depois de amanha") -> cal.add(Calendar.DAY_OF_YEAR, 2)
            Regex("\\bdaqui a (\\d{1,3}) dias?\\b").find(normalized) != null -> {
                val days = Regex("\\bdaqui a (\\d{1,3}) dias?\\b").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                if (days !in 1..365) return null
                cal.add(Calendar.DAY_OF_YEAR, days)
            }
            Regex("\\bamanha\\b").containsMatchIn(normalized) -> cal.add(Calendar.DAY_OF_YEAR, 1)
            Regex("\\bhoje\\b").containsMatchIn(normalized) -> Unit
            else -> {
                val monthNames = mapOf(
                    "janeiro" to Calendar.JANUARY, "fevereiro" to Calendar.FEBRUARY,
                    "marco" to Calendar.MARCH, "abril" to Calendar.APRIL,
                    "maio" to Calendar.MAY, "junho" to Calendar.JUNE,
                    "julho" to Calendar.JULY, "agosto" to Calendar.AUGUST,
                    "setembro" to Calendar.SEPTEMBER, "outubro" to Calendar.OCTOBER,
                    "novembro" to Calendar.NOVEMBER, "dezembro" to Calendar.DECEMBER
                )
                val namedDate = Regex("\\b(?:dia\\s+)?(\\d{1,2})\\s+de\\s+(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)(?:\\s+de\\s+(\\d{4}))?\\b").find(normalized)
                if (namedDate != null) {
                    val day = namedDate.groupValues[1].toIntOrNull() ?: return null
                    val month = monthNames[namedDate.groupValues[2]] ?: return null
                    val year = namedDate.groupValues[3].toIntOrNull() ?: cal.get(Calendar.YEAR)
                    cal.setLenient(false)
                    try {
                        cal.set(year, month, day, 0, 0, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        cal.time
                    } catch (_: Exception) { return null }
                    if (namedDate.groupValues[3].isBlank() && cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.YEAR, 1)
                    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                }
                val weekdays = listOf(
                    "domingo" to Calendar.SUNDAY,
                    "segunda" to Calendar.MONDAY, "segunda feira" to Calendar.MONDAY,
                    "terca" to Calendar.TUESDAY, "terca feira" to Calendar.TUESDAY,
                    "quarta" to Calendar.WEDNESDAY, "quarta feira" to Calendar.WEDNESDAY,
                    "quinta" to Calendar.THURSDAY, "quinta feira" to Calendar.THURSDAY,
                    "sexta" to Calendar.FRIDAY, "sexta feira" to Calendar.FRIDAY,
                    "sabado" to Calendar.SATURDAY
                )
                val day = weekdays.firstOrNull { (name, _) ->
                    Regex("\\b" + Regex.escape(name) + "\\b").containsMatchIn(normalized)
                }?.second
                if (day != null) {
                    val current = cal.get(Calendar.DAY_OF_WEEK)
                    var delta = (day - current + 7) % 7
                    if (delta == 0) delta = 7
                    cal.add(Calendar.DAY_OF_YEAR, delta)
                } else {
                    val explicit = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b").find(normalized)
                        ?: return null
                    val d = explicit.groupValues[1].toIntOrNull() ?: return null
                    val m = explicit.groupValues[2].toIntOrNull() ?: return null
                    val yRaw = explicit.groupValues[3]
                    val year = if (yRaw.isBlank()) cal.get(Calendar.YEAR) else {
                        val parsed = yRaw.toIntOrNull() ?: return null
                        if (parsed < 100) 2000 + parsed else parsed
                    }
                    cal.setLenient(false)
                    try { cal.set(year, m - 1, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0); cal.time }
                    catch (_: Exception) { return null }
                    if (yRaw.isBlank() && cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.YEAR, 1)
                }
            }
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
}
