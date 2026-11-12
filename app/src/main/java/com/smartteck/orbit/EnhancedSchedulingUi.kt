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
    onSave: (String, String, String) -> Unit
) {
    var title by remember(initialTask?.id) { mutableStateOf(initialTask?.title.orEmpty()) }
    var date by remember(initialTask?.id) { mutableStateOf(initialTask?.dueDate.orEmpty()) }
    var time by remember(initialTask?.id) { mutableStateOf(initialTask?.reminderTime ?: "09:00") }
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
                if (title.isNotBlank() && validDate && validTime && futureSchedule) onSave(title.trim(), date, time.trim())
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
internal fun EnhancedAssistantDialog(onDismiss: () -> Unit, onCreateTask: (String, String, String, Int) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Olá! Escreva o compromisso que deseja adicionar à sua agenda.") }
    var title by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf("09:00") }
    var stage by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<OrbitScheduleDraft?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)

    fun sendCommitment() {
        val value = prompt.trim()
        if (value.isBlank()) return
        title = value
        draft = null
        stage = 1
        answer = "Compromisso recebido: $value. Agora escolha o horário no relógio abaixo."
        prompt = ""
    }

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
            Column(Modifier.fillMaxWidth().heightIn(max = 550.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                if (stage == 0) {
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Qual é o compromisso?") },
                        placeholder = { Text("Ex.: Consulta no dentista") },
                        minLines = 2, maxLines = 3, shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = { sendCommitment() }, enabled = prompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SBlue), shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("ENVIAR COMPROMISSO")
                    }
                }

                if (stage == 1) {
                    Surface(color = SPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("02  •  DEFINA O HORÁRIO", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            TimePicker(state = timeState)
                            Button(
                                onClick = {
                                    selectedTime = "%02d:%02d".format(Locale.US, timeState.hour, timeState.minute)
                                    stage = 2
                                    answer = "Horário definido para $selectedTime. Agora escolha o dia e o mês no calendário."
                                },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("CONFIRMAR HORÁRIO")
                            }
                        }
                    }
                }

                if (stage == 2) {
                    Surface(color = SPanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("03  •  ESCOLHA O DIA E O MÊS", color = SCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, null, tint = SCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Horário selecionado: $selectedTime", color = SText, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("ABRIR CALENDÁRIO E ESCOLHER DATA")
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
                                Text(item.title, color = SText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarMonth, null, tint = SMuted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                                    Text(scheduleDisplayDate(item.date), color = SText, fontSize = 12.sp); Spacer(Modifier.width(12.dp))
                                    Icon(Icons.Default.Schedule, null, tint = SMuted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
                                    Text(item.time, color = SText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        onCreateTask(item.title, item.date, item.time, item.reminderLeadMinutes)
                                        draft = null
                                        title = ""
                                        stage = 0
                                        answer = "COMPROMISSO AGENDADO. Seu lembrete foi programado."
                                    },
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

                if (stage > 0 && draft == null) {
                    TextButton(onClick = {
                        stage = 0
                        title = ""
                        answer = "Tudo bem. Escreva novamente o compromisso que deseja adicionar."
                    }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Cancelar e começar novamente", color = SMuted)
                    }
                }
                Text("O compromisso só será salvo depois que você revisar e confirmar.", color = SMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = SMuted) } }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis
                    if (millis != null) {
                        val chosenDate = scheduleFormatDate(millis)
                        if (scheduleIsFuture(chosenDate, selectedTime)) {
                            draft = OrbitScheduleDraft(title, chosenDate, selectedTime, 0)
                            answer = "Data e horário definidos. Confira o resumo antes de confirmar."
                        } else {
                            answer = "Essa data e esse horário já passaram. Escolha uma data futura."
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
