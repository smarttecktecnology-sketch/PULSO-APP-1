package com.smartteck.orbit

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val SBg = Color(0xFF071321)
private val SPanel = Color(0xFF10243A)
private val SPanel2 = Color(0xFF162E49)
private val SBlue = Color(0xFF3188FF)
private val SCyan = Color(0xFF54D6E8)
private val SText = Color(0xFFF4F8FF)
private val SMuted = Color(0xFF9DB0C7)
private val SGreen = Color(0xFF45C99A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnhancedAddTaskDialog(
    onDismiss: () -> Unit,
    initialTask: OrbitTask? = null,
    onSave: (String, String, String, String) -> Unit
) {
    var title by remember(initialTask?.id) { mutableStateOf(initialTask?.title.orEmpty()) }
    var date by remember(initialTask?.id) { mutableStateOf(initialTask?.dueDate.orEmpty()) }
    var time by remember(initialTask?.id) { mutableStateOf(initialTask?.reminderTime ?: "09:00") }
    var recurrence by remember(initialTask?.id) { mutableStateOf("once") }
    var recurrenceExpanded by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var validate by remember { mutableStateOf(false) }
    val validTime = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(time.trim())
    val validDate = date.isNotBlank() && scheduleParseDate(date) != null
    val futureSchedule = validDate && validTime && scheduleIsFuture(date, time)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SBg, titleContentColor = SText, textContentColor = SText,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(SBlue.copy(alpha = .2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EventAvailable, null, tint = SCyan)
                }
                Column {
                    Text(if (initialTask == null) "Novo compromisso" else "Editar compromisso", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Configure seu lembrete", color = SMuted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 510.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("01  •  O QUE VOCÊ PRECISA LEMBRAR?", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome do compromisso") }, placeholder = { Text("Ex.: Consulta, reunião, estudar...") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Text("02  •  QUANDO VAI ACONTECER?", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = if (date.isBlank()) "" else scheduleDisplayDate(date), onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Data do compromisso") }, placeholder = { Text("Escolher data") },
                    trailingIcon = { IconButton(onClick = { showDate = true }) { Icon(Icons.Default.CalendarMonth, "Selecionar data", tint = SCyan) } },
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = time, onValueChange = { time = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Horário do lembrete") }, placeholder = { Text("09:00") }, singleLine = true, isError = !validTime,
                    trailingIcon = { IconButton(onClick = { showTime = true }) { Icon(Icons.Default.Schedule, "Selecionar horário", tint = SCyan) } },
                    supportingText = { Text("Formato 24 horas • use o relógio para escolher") }, shape = RoundedCornerShape(14.dp)
                )
                if (initialTask == null) {
                    Text("03  •  REPETIÇÃO", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(onClick = { recurrenceExpanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.Repeat, null, tint = SCyan)
                            Spacer(Modifier.width(8.dp))
                            Text(when (recurrence) { "daily" -> "Todos os dias"; "weekly" -> "Toda semana"; "monthly" -> "Todo mês"; else -> "Não repetir" })
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null, tint = SMuted)
                        }
                        DropdownMenu(expanded = recurrenceExpanded, onDismissRequest = { recurrenceExpanded = false }) {
                            listOf("once" to "Não repetir", "daily" to "Todos os dias", "weekly" to "Toda semana", "monthly" to "Todo mês").forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { recurrence = value; recurrenceExpanded = false })
                            }
                        }
                    }
                    if (recurrence != "once") Text("Serão criadas até 12 ocorrências, com lembretes individuais.", color = SMuted, fontSize = 11.sp)
                }
                AnimatedVisibility(validate && (title.isBlank() || !validDate || !validTime || !futureSchedule), enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                    Surface(color = Color(0xFF542B38), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            if (title.isBlank()) "Informe o nome do compromisso." else if (!validDate) "Selecione uma data válida para ativar o lembrete." else if (!validTime) "Informe um horário válido." else "Escolha uma data e um horário futuros.",
                            color = Color(0xFFFFC5CF), fontSize = 12.sp, modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Surface(color = SBlue.copy(alpha = .12f), shape = RoundedCornerShape(13.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = SCyan)
                        Spacer(Modifier.width(9.dp))
                        Text("O ORBIT avisará você na data e no horário escolhidos.", color = SMuted, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                validate = true
                if (title.isNotBlank() && validDate && validTime && futureSchedule) onSave(title.trim(), date, time.trim(), recurrence)
            }, colors = ButtonDefaults.buttonColors(containerColor = SBlue), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Salvar compromisso")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = SMuted) } }
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = scheduleParseDate(date))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = scheduleFormatDate(it) }; showDate = false }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state, showModeToggle = false) }
    }
    if (showTime) {
        val parts = time.split(":")
        val state = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9,
            initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false }, containerColor = SPanel, titleContentColor = SText,
            title = { Text("Escolher horário") }, text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { time = "%02d:%02d".format(Locale.US, state.hour, state.minute); showTime = false }) { Text("Aplicar") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Voltar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnhancedAssistantDialog(
    tasks: List<OrbitTask>,
    onDismiss: () -> Unit,
    onCreateTask: (String, String, String, Int, String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Olá! Escreva o compromisso que deseja adicionar à sua agenda.") }
    var title by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf("09:00") }
    var selectedDate by remember { mutableStateOf("") }
    var stage by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<OrbitScheduleDraft?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf("once") }
    var recurrenceExpanded by remember { mutableStateOf(false) }

    fun resetFlow() {
        prompt = ""
        title = ""
        selectedTime = "09:00"
        selectedDate = ""
        recurrence = "once"
        draft = null
        stage = 0
        answer = "Tudo bem. Escreva novamente o compromisso que deseja adicionar."
    }

    val conflictingDates: List<String> = if (selectedDate.isNotBlank()) {
        orbitRecurrenceDates(selectedDate, recurrence).filter { date ->
            tasks.any { !it.done && it.dueDate == date && it.reminderTime == selectedTime }
        }
    } else emptyList()
    val hasConflict = conflictingDates.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SBg, titleContentColor = SText, textContentColor = SText,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(44.dp).background(SBlue.copy(alpha = .2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = SCyan, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text("ORBIT Assistente", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(SGreen, CircleShape)); Spacer(Modifier.width(6.dp))
                        Text("AGENDAMENTO GUIADO", color = SMuted, fontSize = 9.sp, letterSpacing = .5.sp)
                    }
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = SPanel2, shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CONVERSA", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(answer, color = SText, fontSize = 13.sp, lineHeight = 19.sp)
                        if (title.isNotBlank()) {
                            Text("COMPROMISSO", color = SMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(title, color = SText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                AnimatedContent(targetState = stage, label = "orbit_schedule_steps") { currentStage ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (currentStage) {
                            0 -> {
                                OutlinedTextField(
                                    value = prompt, onValueChange = { prompt = it },
                                    modifier = Modifier.fillMaxWidth(), label = { Text("Qual é o compromisso?") },
                                    placeholder = { Text("Ex.: Consulta no dentista") },
                                    minLines = 2, maxLines = 3, shape = RoundedCornerShape(14.dp)
                                )
                                Button(
                                    onClick = {
                                        val value = prompt.trim()
                                        if (value.isNotBlank()) {
                                            title = OrbitCommandParser.suggestTitle(value)
                                            draft = null
                                            selectedDate = ""
                                            recurrence = "once"
                                            stage = 1
                                            answer = "Compromisso recebido. Agora escolha o horário no relógio."
                                            prompt = ""
                                        }
                                    }, enabled = prompt.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SBlue), shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Send, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("ENVIAR COMPROMISSO")
                                }
                            }
                            1 -> {
                                Surface(color = SPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("01  •  DEFINA O HORÁRIO", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        key(stage) {
                                            val hour = selectedTime.substringBefore(":").toIntOrNull()?.coerceIn(0, 23) ?: 9
                                            val minute = selectedTime.substringAfter(":", "00").toIntOrNull()?.coerceIn(0, 59) ?: 0
                                            val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
                                            TimePicker(state = timeState)
                                            Button(
                                                onClick = {
                                                    selectedTime = "%02d:%02d".format(Locale.US, timeState.hour, timeState.minute)
                                                    stage = 2
                                                    answer = "Horário definido para $selectedTime. Agora escolha a data no calendário."
                                                },
                                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SBlue),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("CONFIRMAR HORÁRIO")
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Surface(color = SPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("02  •  ESCOLHA A DATA", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, null, tint = SCyan)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Horário: $selectedTime", color = SText, fontWeight = FontWeight.SemiBold)
                                        }
                                        Button(
                                            onClick = { showDatePicker = true },
                                            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SBlue),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                            Text(if (selectedDate.isBlank()) "ABRIR CALENDÁRIO" else scheduleDisplayDate(selectedDate))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(draft != null, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }), exit = fadeOut()) {
                    draft?.let { item ->
                        Surface(color = SBlue.copy(alpha = .14f), shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.EventAvailable, null, tint = SCyan)
                                    Spacer(Modifier.width(7.dp)); Text("REVISÃO DO AGENDAMENTO", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedTextField(
                                    value = title, onValueChange = { title = it },
                                    modifier = Modifier.fillMaxWidth(), label = { Text("Nome do compromisso") },
                                    singleLine = true, shape = RoundedCornerShape(12.dp)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarMonth, null, tint = SMuted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp)); Text(scheduleDisplayDate(item.date), color = SText, fontSize = 12.sp)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { draft = null; stage = 2; answer = "Escolha outra data para o compromisso." }) { Text("Editar data") }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = SMuted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp)); Text(selectedTime, color = SText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { draft = null; stage = 1; answer = "Escolha outro horário para o compromisso." }) { Text("Editar horário") }
                                }
                                Box {
                                    OutlinedButton(onClick = { recurrenceExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Repeat, null, tint = SCyan); Spacer(Modifier.width(8.dp))
                                        Text("Repetição: " + when (recurrence) { "daily" -> "Diariamente"; "weekly" -> "Semanalmente"; "monthly" -> "Mensalmente"; else -> "Não repetir" })
                                        Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                    DropdownMenu(expanded = recurrenceExpanded, onDismissRequest = { recurrenceExpanded = false }) {
                                        listOf("once" to "Não repetir", "daily" to "Diariamente", "weekly" to "Semanalmente", "monthly" to "Mensalmente").forEach { option ->
                                            DropdownMenuItem(text = { Text(option.second) }, onClick = {
                                                recurrence = option.first
                                                recurrenceExpanded = false
                                            })
                                        }
                                    }
                                }
                                if (hasConflict) {
                                    Surface(color = Color(0xFF542B38), shape = RoundedCornerShape(12.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC5CF))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Conflito de horário: já existe compromisso nesse horário em " +
                                                    conflictingDates.joinToString { scheduleDisplayDate(it) } +
                                                    ". Altere a data, o horário ou a repetição.",
                                                color = Color(0xFFFFC5CF), fontSize = 11.sp, lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    if (recurrence == "once") "Será criado 1 compromisso." else "Serão criadas até 12 ocorrências, uma para cada período.",
                                    color = SMuted, fontSize = 10.sp
                                )
                                Button(
                                    onClick = {
                                        if (title.isNotBlank() && !hasConflict && scheduleIsFuture(item.date, selectedTime)) {
                                            onCreateTask(title.trim(), item.date, selectedTime, item.reminderLeadMinutes, recurrence)
                                            resetFlow()
                                            answer = "COMPROMISSO AGENDADO. Seu lembrete foi programado."
                                        }
                                    },
                                    enabled = title.isNotBlank() && !hasConflict && scheduleIsFuture(item.date, selectedTime),
                                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp))
                                    Text("CONFIRMAR AGENDAMENTO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (stage > 0) {
                    TextButton(onClick = { resetFlow() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Cancelar e começar novamente", color = SMuted)
                    }
                }
                Text("O compromisso só será salvo depois da revisão e confirmação. O ORBIT também verifica conflitos de horário.", color = SMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = SMuted) } }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = if (selectedDate.isBlank()) null else scheduleParseDate(selectedDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis
                    if (millis != null) {
                        val chosenDate = scheduleFormatDate(millis)
                        if (scheduleIsFuture(chosenDate, selectedTime)) {
                            selectedDate = chosenDate
                            draft = OrbitScheduleDraft(title, chosenDate, selectedTime, 0)
                            stage = 2
                            answer = "Data e horário definidos. Confira os dados, edite se necessário e confirme."
                        } else {
                            answer = "Essa data e esse horário já passaram. Escolha uma data e um horário futuros."
                        }
                    }
                    showDatePicker = false
                }) { Text("Confirmar data") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dateState, showModeToggle = false) }
    }
}

private fun scheduleParseDate(value: String): Long? {
    if (value.isBlank()) return null
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val p = java.text.ParsePosition(0)
    val d = f.parse(value, p)
    return if (d != null && p.index == value.length) d.time else null
}
private fun scheduleFormatDate(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date(value))
private fun scheduleDisplayDate(value: String): String =
    scheduleParseDate(value)?.let { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it)) } ?: value

private fun scheduleIsFuture(date: String, time: String): Boolean =
    orbitLocalDateTimeMillis(date, time)?.let { it > System.currentTimeMillis() } ?: false
