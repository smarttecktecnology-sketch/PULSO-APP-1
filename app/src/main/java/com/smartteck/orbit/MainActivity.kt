package com.smartteck.orbit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

private val OrbitBg = Color(0xFF071321)
private val OrbitPanel = Color(0xFF10243A)
private val OrbitPanel2 = Color(0xFF162E49)
private val OrbitBlue = Color(0xFF3188FF)
private val OrbitCyan = Color(0xFF54D6E8)
private val OrbitText = Color(0xFFF4F8FF)
private val OrbitMuted = Color(0xFF9DB0C7)
private val OrbitGreen = Color(0xFF45C99A)

data class OrbitTask(val id: Long, val title: String, val dueDate: String, val done: Boolean, val reminderTime: String = "09:00", val reminderLeadMinutes: Int = 0)
data class OrbitDocument(val uri: String, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OrbitApp() }
    }
}

private enum class OrbitPage(val title: String) { HOME("Início"), TASKS("Tarefas"), AGENDA("Agenda"), FILES("Arquivos"), NOTIFICATIONS("Notificações"), PLANS("Assinaturas"), SETTINGS("Configurações") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrbitApp() {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1800); showSplash = false }
    var page by remember { mutableStateOf(OrbitPage.HOME) }
    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var documents by remember { mutableStateOf(loadDocuments(context)) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<OrbitTask?>(null) }
    var showAssistant by remember { mutableStateOf(false) }
    var activeTier by remember { mutableStateOf(OrbitPlanTier.FREE) }
    var plusPrice by remember { mutableStateOf<String?>(null) }
    var maxPrice by remember { mutableStateOf<String?>(null) }
    var plusReady by remember { mutableStateOf(false) }
    var maxReady by remember { mutableStateOf(false) }
    var premiumStatus by remember { mutableStateOf("Preparando planos do Google Play…") }
    var voiceRemindersEnabled by remember { mutableStateOf(context.getSharedPreferences("orbit_settings", Context.MODE_PRIVATE).getBoolean("voice_reminders", false)) }
    val effectiveTier = if (BuildConfig.ORBIT_TEST_MODE) OrbitPlanTier.MAX else activeTier
    val voiceAvailable = effectiveTier == OrbitPlanTier.PLUS || effectiveTier == OrbitPlanTier.MAX
    val billingManager = remember(context) {
        if (BuildConfig.ORBIT_TEST_MODE) null else OrbitBillingManager(context) { tier, status, plus, max, plusIsReady, maxIsReady ->
            activeTier = tier
            context.getSharedPreferences("orbit_settings", Context.MODE_PRIVATE)
                .edit().putString("active_plan", tier.name).apply()
            premiumStatus = status
            plusPrice = plus
            maxPrice = max
            plusReady = plusIsReady
            maxReady = maxIsReady
        }
    }
    DisposableEffect(billingManager) {
        billingManager?.connect()
        onDispose { billingManager?.close() }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val payload = buildOrbitBackup(context)
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "Backup exportado com sucesso.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Não foi possível exportar o backup.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw IllegalStateException("Arquivo vazio")
                val restored = parseOrbitBackup(raw)
                tasks = restored.tasks
                saveTasks(context, restored.tasks)
                context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE).edit()
                    .putString("notification_history", restored.notificationHistory).apply()
                Toast.makeText(context, "Backup restaurado: ${restored.tasks.size} tarefa(s).", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Backup inválido ou incompatível. Seus dados atuais foram preservados.", Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(Unit) {
        createOrbitNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Recria os lembretes das tarefas pendentes ao abrir o app.
    // O WorkManager mantém os trabalhos já agendados entre reinicializações.
    LaunchedEffect(tasks, voiceRemindersEnabled, voiceAvailable) {
        tasks.forEach { task ->
            if (task.done) cancelOrbitReminder(context, task.id)
            else scheduleOrbitReminder(context, task, voiceRemindersEnabled && voiceAvailable)
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            val name = queryDisplayName(context, uri)
            if (documents.none { it.uri == uri.toString() }) {
                documents = documents + OrbitDocument(uri.toString(), name)
                saveDocuments(context, documents)
            }
        }
    }

    fun addTask(title: String, date: String, time: String, leadMinutes: Int = 0, recurrence: String = "once") {
        val cleanTitle = title.trim()
        val cleanDate = date.trim()
        val cleanTime = time.trim()
        val dates = orbitRecurrenceDates(cleanDate, recurrence)
        if (cleanTitle.isBlank() || dates.isEmpty() || orbitLocalDateTimeMillis(cleanDate, cleanTime)?.let { it > System.currentTimeMillis() } != true) {
            Toast.makeText(context, "Confira o nome, a data e o horário futuro do compromisso.", Toast.LENGTH_LONG).show()
            return
        }
        val conflictingDates = orbitConflictingScheduleDates(tasks, dates, cleanTime)
        val firstAvailableId = nextOrbitTaskId(tasks.map { it.id })
        val newTasks = dates.mapIndexedNotNull { index, occurrenceDate ->
            if (occurrenceDate in conflictingDates) null else OrbitTask(
                firstAvailableId + index,
                cleanTitle,
                occurrenceDate,
                false,
                cleanTime,
                leadMinutes.coerceIn(0, 1440)
            )
        }
        if (newTasks.isEmpty()) {
            Toast.makeText(context, "Já existe um compromisso nesse horário.", Toast.LENGTH_LONG).show()
            return
        }
        tasks = tasks + newTasks
        saveTasks(context, tasks)
        newTasks.forEach { scheduleOrbitReminder(context, it, voiceRemindersEnabled && voiceAvailable) }
        if (newTasks.size < dates.size) {
            Toast.makeText(context, "${newTasks.size} ocorrência(s) criada(s); horários conflitantes foram ignorados.", Toast.LENGTH_LONG).show()
        } else if (recurrence != "once") {
            Toast.makeText(context, "${newTasks.size} ocorrências recorrentes adicionadas à agenda.", Toast.LENGTH_SHORT).show()
        }
    }
    fun updateTask(updated: OrbitTask) {
        if (updated.title.isBlank() || (!updated.done && orbitLocalDateTimeMillis(updated.dueDate, updated.reminderTime)?.let { it > System.currentTimeMillis() } != true)) {
            Toast.makeText(context, "Confira o nome, a data e o horário futuro do compromisso.", Toast.LENGTH_LONG).show()
            return
        }
        val conflicts = if (updated.done) emptySet() else orbitConflictingScheduleDates(
            tasks, listOf(updated.dueDate), updated.reminderTime, excludingTaskId = updated.id
        )
        if (conflicts.isNotEmpty()) {
            Toast.makeText(context, "Já existe outro compromisso nesse horário. Escolha outro horário.", Toast.LENGTH_LONG).show()
            return
        }
        tasks = tasks.map { if (it.id == updated.id) updated else it }
        saveTasks(context, tasks)
        if (updated.done) cancelOrbitReminder(context, updated.id) else scheduleOrbitReminder(context, updated, voiceRemindersEnabled && voiceAvailable)
    }
    fun removeTask(task: OrbitTask) {
        tasks = tasks.filterNot { it.id == task.id }
        saveTasks(context, tasks)
        cancelOrbitReminder(context, task.id)
    }

    val openAssistant = {
        if (effectiveTier == OrbitPlanTier.MAX) showAssistant = true
        else {
            page = OrbitPage.PLANS
            Toast.makeText(context, "O assistente de IA é exclusivo do ORBIT MAX.", Toast.LENGTH_LONG).show()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = OrbitBlue, background = OrbitBg, surface = OrbitPanel, onBackground = OrbitText, onSurface = OrbitText)) {
        if (showSplash) {
            OrbitSplashScreen()
        } else {
        Scaffold(
            containerColor = OrbitBg,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OrbitLogoMark(38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("SMART", fontSize = 10.sp, color = OrbitMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text("ORBIT", fontSize = 21.sp, color = OrbitText, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { page = OrbitPage.SETTINGS }) {
                            Icon(Icons.Default.Settings, "Configurações", tint = OrbitCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitBg)
                )
            },
            bottomBar = {
                if (page !in listOf(OrbitPage.PLANS, OrbitPage.SETTINGS, OrbitPage.NOTIFICATIONS)) {
                    NavigationBar(containerColor = OrbitPanel) {
                        val nav = listOf(OrbitPage.HOME to Icons.Default.Home, OrbitPage.TASKS to Icons.Default.CheckCircle, OrbitPage.AGENDA to Icons.Default.CalendarMonth, OrbitPage.FILES to Icons.Default.FolderOpen)
                        nav.forEach { (item, icon) ->
                            NavigationBarItem(
                                selected = page == item, onClick = { page = item },
                                icon = { Icon(icon, item.title) }, label = { Text(item.title, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = OrbitCyan, indicatorColor = OrbitBlue.copy(alpha = .35f), unselectedIconColor = OrbitMuted, unselectedTextColor = OrbitMuted)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                when (page) {
                    OrbitPage.HOME, OrbitPage.TASKS -> FloatingActionButton(onClick = { showTaskDialog = true }, containerColor = OrbitBlue, contentColor = Color.White) { Icon(Icons.Default.Add, "Nova tarefa") }
                    OrbitPage.FILES -> FloatingActionButton(onClick = { documentPicker.launch(arrayOf("*/*")) }, containerColor = OrbitBlue, contentColor = Color.White) { Icon(Icons.Default.UploadFile, "Adicionar arquivo") }
                    OrbitPage.PLANS, OrbitPage.SETTINGS -> Unit
                    else -> Unit
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    OrbitPage.HOME -> HomeScreen(effectiveTier, tasks, documents.size, { page = OrbitPage.TASKS }, { page = OrbitPage.AGENDA }, openAssistant, { if (BuildConfig.ORBIT_TEST_MODE) Toast.makeText(context, "Todos os recursos estão liberados na versão de teste.", Toast.LENGTH_SHORT).show() else page = OrbitPage.PLANS }, { updateTask(it.copy(done = !it.done)) })
                    OrbitPage.TASKS -> TasksScreen(tasks, { updateTask(it.copy(done = !it.done)) }, { removeTask(it) }, { editingTask = it })
                    OrbitPage.AGENDA -> AgendaScreen(tasks)
                    OrbitPage.FILES -> FilesScreen(documents, { documentPicker.launch(arrayOf("*/*")) }, {
                        documents = documents.filterNot { doc -> doc.uri == it.uri }; saveDocuments(context, documents)
                    })
                    OrbitPage.PLANS -> OrbitPlansScreen(
                        effectiveTier, plusPrice, maxPrice, plusReady, maxReady, premiumStatus,
                        onSubscribe = { tier ->
                            val activity = context as? Activity
                            if (activity != null) billingManager?.launchPurchase(activity, tier)
                            else Toast.makeText(context, "Não foi possível abrir o Google Play.", Toast.LENGTH_SHORT).show()
                        },
                        onBack = { page = OrbitPage.HOME }
                    )
                    OrbitPage.NOTIFICATIONS -> NotificationHistoryScreen(
                        entries = loadOrbitNotificationHistory(context),
                        onClear = {
                            context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE).edit().putString("notification_history", "[]").apply()
                            page = OrbitPage.NOTIFICATIONS
                        },
                        onBack = { page = OrbitPage.SETTINGS }
                    )
                    OrbitPage.SETTINGS -> OrbitSettingsScreen(
                        effectiveTier, voiceRemindersEnabled,
                        onVoiceChanged = { enabled ->
                            if (voiceAvailable) {
                                voiceRemindersEnabled = enabled
                                context.getSharedPreferences("orbit_settings", Context.MODE_PRIVATE).edit().putBoolean("voice_reminders", enabled).apply()
                                Toast.makeText(context, if (enabled) "Lembretes por voz ativados" else "Lembretes por voz desativados", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onHistory = { page = OrbitPage.NOTIFICATIONS },
                        onBackup = { backupExporter.launch("smart-orbit-backup.json") },
                        onRestore = { backupImporter.launch(arrayOf("application/json", "text/*")) },
                        onBack = { page = OrbitPage.HOME }
                    )
                }
            }
        }
        if (showTaskDialog || editingTask != null) {
            val taskBeingEdited = editingTask
            EnhancedAddTaskDialog(
                onDismiss = { showTaskDialog = false; editingTask = null },
                initialTask = taskBeingEdited,
                recurrenceAvailable = effectiveTier == OrbitPlanTier.PLUS || effectiveTier == OrbitPlanTier.MAX
            ) { title, date, time, recurrence ->
                if (taskBeingEdited == null) addTask(title, date, time, recurrence = recurrence)
                else updateTask(taskBeingEdited.copy(title = title.trim(), dueDate = date.trim(), reminderTime = time))
                showTaskDialog = false
                editingTask = null
            }
        }
        if (showAssistant && effectiveTier == OrbitPlanTier.MAX) EnhancedAssistantDialog(
            tasks = tasks,
            onDismiss = { showAssistant = false },
            onCreateTask = { title, date, time, lead, recurrence -> addTask(title, date, time, lead, recurrence) }
        )
        }
    }
}


@Composable
private fun OrbitLogoMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(OrbitBlue, RoundedCornerShape(size * 0.30f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.68f).border(1.6.dp, OrbitCyan, CircleShape))
        Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(size * 0.40f))
    }
}

@Composable
private fun OrbitSplashScreen() {
    Box(Modifier.fillMaxSize().background(OrbitBg), contentAlignment = Alignment.Center) {
        Box(Modifier.size(270.dp).background(OrbitBlue.copy(alpha = 0.07f), CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(124.dp).background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(OrbitBlue, Color(0xFF174B91))),
                    shape = RoundedCornerShape(36.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(88.dp).border(2.dp, OrbitCyan, CircleShape))
                Box(Modifier.size(58.dp).border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape))
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(39.dp))
                Box(Modifier.size(12.dp).offset(x = 39.dp, y = (-32).dp).background(OrbitCyan, CircleShape))
            }
            Spacer(Modifier.height(26.dp))
            Text("SMART", color = OrbitMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Text("ORBIT", color = OrbitText, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            Spacer(Modifier.height(10.dp))
            Text("Sua vida organizada. Seu tempo sob controle.", color = OrbitMuted, fontSize = 12.sp)
            Spacer(Modifier.height(34.dp))
            CircularProgressIndicator(Modifier.size(22.dp), color = OrbitCyan, strokeWidth = 2.dp)
        }
        Text("SMART TECK", Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), color = OrbitMuted.copy(alpha = 0.65f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
private fun HomeScreen(activeTier: OrbitPlanTier, tasks: List<OrbitTask>, documentCount: Int, onTasks: () -> Unit, onAgenda: () -> Unit, onAssistant: () -> Unit, onPlans: () -> Unit, onToggle: (OrbitTask) -> Unit) {
    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val todayTasks = tasks.filter { it.dueDate == todayKey }
    val todayDone = todayTasks.count { it.done }
    val todayPending = todayTasks.size - todayDone
    val pending = tasks.count { !it.done }
    val completed = tasks.count { it.done }
    val overdue = tasks.count { !it.done && OrbitTaskTime.isOverdue(it) }
    val nextTask = OrbitTaskTime.sortByDueDate(tasks.filter { !it.done && it.dueDate >= todayKey }).firstOrNull()
    val today = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date())
    val completion = if (todayTasks.isEmpty()) 0f else todayDone.toFloat() / todayTasks.size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlans, colors = ButtonDefaults.buttonColors(containerColor = OrbitBlue), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp)); Text("Assinaturas")
                }
            }
            Text(today.replaceFirstChar { it.uppercase() }, color = OrbitMuted, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text("Seu mundo, em ordem.", color = OrbitText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Seu painel diário de organização.", color = OrbitMuted, fontSize = 14.sp)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = OrbitPanel2)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AGENDAMENTOS DE HOJE", color = OrbitCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${todayDone}/${todayTasks.size}", color = OrbitText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    LinearProgressIndicator(progress = completion, modifier = Modifier.fillMaxWidth().height(7.dp), color = OrbitCyan, trackColor = OrbitBg)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniMetric("$todayPending pendentes", OrbitCyan)
                        MiniMetric("$todayDone concluídos", OrbitGreen)
                    }
                    Text(if (todayTasks.isEmpty()) "Você ainda não tem compromissos para hoje." else "Você concluiu $todayDone de ${todayTasks.size} agendamento(s) de hoje.", color = OrbitMuted, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardStat("Pendentes", pending.toString(), Icons.Default.PendingActions, Modifier.weight(1f))
                DashboardStat("Atrasados", overdue.toString(), Icons.Default.WarningAmber, Modifier.weight(1f))
                DashboardStat("Concluídos", completed.toString(), Icons.Default.TaskAlt, Modifier.weight(1f))
            }
        }
        if (activeTier == OrbitPlanTier.MAX) item {
            Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = OrbitCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("ORBIT MAX • CENTRAL INTELIGENTE", color = OrbitCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        when {
                            overdue > 0 -> "Você tem $overdue compromisso(s) atrasado(s). Priorize a revisão deles."
                            todayPending > 0 -> "Você tem $todayPending compromisso(s) para concluir hoje. Mantenha o foco."
                            todayTasks.isEmpty() -> "Sua agenda de hoje está livre. Aproveite para planejar os próximos dias."
                            else -> "Ótimo ritmo: seus compromissos de hoje estão concluídos."
                        },
                        color = OrbitText, fontSize = 13.sp
                    )
                    Text("Use o Assistente MAX para organizar novos compromissos e conferir conflitos.", color = OrbitMuted, fontSize = 11.sp)
                    Button(onClick = onAssistant, colors = ButtonDefaults.buttonColors(containerColor = OrbitBlue)) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("Abrir assistente MAX")
                    }
                }
            }
        }
        if (activeTier == OrbitPlanTier.PLUS) item {
            Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ORBIT PLUS • RESUMO DA ROTINA", color = OrbitCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("$pending compromisso(s) pendente(s) e $completed concluído(s).", color = OrbitText, fontSize = 13.sp)
                    Text("Aproveite os lembretes por voz e os agendamentos recorrentes.", color = OrbitMuted, fontSize = 11.sp)
                }
            }
        }
        if (activeTier == OrbitPlanTier.MAX) item { OrbitProgressPanel(tasks) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("PRÓXIMO COMPROMISSO", color = OrbitCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (nextTask == null) {
                        Text("Nenhum compromisso futuro pendente.", color = OrbitText, fontSize = 13.sp)
                    } else {
                        Text(nextTask.title, color = OrbitText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${OrbitTaskTime.displayDate(nextTask.dueDate)} • ${nextTask.reminderTime}", color = OrbitMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        item { SectionHeader("Acesso rápido", "Atalhos para sua rotina") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard("Assistente", if (activeTier == OrbitPlanTier.MAX) "Planejamento com IA" else "Recurso do MAX", Icons.Default.AutoAwesome, Modifier.weight(1f), onAssistant)
                ShortcutCard("Agenda", "Calendário mensal", Icons.Default.CalendarMonth, Modifier.weight(1f), onAgenda)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Próximas tarefas", color = OrbitText, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("O que merece sua atenção", color = OrbitMuted, fontSize = 11.sp) }
                Spacer(Modifier.weight(1f)); TextButton(onClick = onTasks) { Text("Ver todas", color = OrbitCyan) }
            }
        }
        if (tasks.none { !it.done }) item { EmptyState("Tudo tranquilo por aqui", "Adicione uma tarefa pelo botão + para começar.", Icons.Default.CheckCircle) }
        else items(OrbitTaskTime.sortByDueDate(tasks.filter { !it.done }).take(4), key = { it.id }) { TaskRow(it, onToggle, null) }
        item {
            Text("$documentCount arquivo(s) guardado(s)", color = OrbitMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = OrbitCyan, modifier = Modifier.size(18.dp))
            Text(value, color = OrbitText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = OrbitMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun OrbitProgressPanel(tasks: List<OrbitTask>) {
    val today = orbitPeriodProgress(tasks, "today")
    val week = orbitPeriodProgress(tasks, "week")
    val month = orbitPeriodProgress(tasks, "month")
    val year = orbitPeriodProgress(tasks, "year")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OrbitPanel),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, null, tint = OrbitCyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("PROGRESSO DOS AGENDAMENTOS", color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Concluídos / total • acompanhe sua rotina", color = OrbitMuted, fontSize = 10.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OrbitProgressMetric("Hoje", today, Modifier.weight(1f))
                OrbitProgressMetric("Semana", week, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OrbitProgressMetric("Mês", month, Modifier.weight(1f))
                OrbitProgressMetric("Ano", year, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OrbitProgressMetric(label: String, progress: Pair<Int, Int>, modifier: Modifier = Modifier) {
    val completed = progress.first
    val total = progress.second
    val remaining = (total - completed).coerceAtLeast(0)
    val progress = if (total == 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .background(OrbitPanel2, RoundedCornerShape(15.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(label, color = OrbitMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text("$completed/$total", color = OrbitText, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = OrbitCyan,
            trackColor = OrbitBg
        )
        Text("$remaining restantes", color = OrbitCyan, fontSize = 10.sp)
    }
}

private fun orbitPeriodProgress(tasks: List<OrbitTask>, period: String): Pair<Int, Int> {
    val now = Calendar.getInstance()
    val start = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        when (period) {
            "week" -> {
                val firstDay = firstDayOfWeek
                val offset = (get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7
                add(Calendar.DAY_OF_MONTH, -offset)
            }
            "month" -> set(Calendar.DAY_OF_MONTH, 1)
            "year" -> set(Calendar.DAY_OF_YEAR, 1)
        }
    }
    val end = (start.clone() as Calendar).apply {
        when (period) {
            "today" -> add(Calendar.DAY_OF_MONTH, 1)
            "week" -> add(Calendar.DAY_OF_MONTH, 7)
            "month" -> add(Calendar.MONTH, 1)
            "year" -> add(Calendar.YEAR, 1)
        }
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val periodTasks = tasks.filter { task ->
        if (task.dueDate.isBlank()) false
        else try {
            val position = java.text.ParsePosition(0)
            val parsed = formatter.parse(task.dueDate, position)
            parsed != null && position.index == task.dueDate.length &&
                parsed.time >= start.timeInMillis && parsed.time < end.timeInMillis
        } catch (_: Exception) {
            false
        }
    }
    return periodTasks.count { it.done } to periodTasks.size
}

@Composable
private fun TasksScreen(tasks: List<OrbitTask>, onToggle: (OrbitTask) -> Unit, onDelete: (OrbitTask) -> Unit, onEdit: (OrbitTask) -> Unit) {
    val pending = tasks.filter { !it.done }; val done = tasks.filter { it.done }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Minhas tarefas", color = OrbitText, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("Acompanhe cada passo e marque o que concluir.", color = OrbitMuted, fontSize = 13.sp) }
        item { SectionHeader("Pendentes", "${pending.size} para fazer") }
        if (pending.isEmpty()) item { EmptyState("Nenhuma pendência", "Você está em dia. Se surgir algo, adicione uma tarefa.", Icons.Default.ThumbUp) }
        items(pending, key = { it.id }) { TaskRow(it, onToggle, onDelete, onEdit = onEdit) }
        if (done.isNotEmpty()) {
            item { Spacer(Modifier.height(5.dp)); SectionHeader("Concluídas", "${done.size} finalizadas") }
            items(done, key = { it.id }) { TaskRow(it, onToggle, onDelete, onEdit = onEdit) }
        }
    }
}

@Composable
private fun AgendaScreen(tasks: List<OrbitTask>) {
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    val monthCalendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, monthOffset)
    }
    val monthLabel = SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR")).format(monthCalendar.time)
        .replaceFirstChar { it.uppercase() }
    val firstWeekday = (monthCalendar.get(Calendar.DAY_OF_WEEK) - monthCalendar.firstDayOfWeek + 7) % 7
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val dayTasks = OrbitTaskTime.sortByDueDate(tasks.filter { it.dueDate == selectedDate })
    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Sua agenda", color = OrbitText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Calendário mensal e compromissos por dia.", color = OrbitMuted, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel2), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthOffset-- }) { Icon(Icons.Default.ChevronLeft, "Mês anterior", tint = OrbitCyan) }
                        Text(monthLabel, color = OrbitText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { monthOffset++ }) { Icon(Icons.Default.ChevronRight, "Próximo mês", tint = OrbitCyan) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("D", "S", "T", "Q", "Q", "S", "S").forEach { day ->
                            Text(day, color = OrbitMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    val totalCells = ((firstWeekday + daysInMonth + 6) / 7) * 7
                    for (weekStart in 0 until totalCells step 7) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (cell in weekStart until weekStart + 7) {
                                val dayNumber = cell - firstWeekday + 1
                                if (dayNumber in 1..daysInMonth) {
                                    val dayCalendar = (monthCalendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayNumber) }
                                    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dayCalendar.time)
                                    val count = tasks.count { it.dueDate == dateKey }
                                    val selected = selectedDate == dateKey
                                    val isToday = todayKey == dateKey
                                    Surface(
                                        onClick = { selectedDate = dateKey },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        color = if (selected) OrbitBlue else if (isToday) OrbitBlue.copy(alpha = .22f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                        border = if (isToday && !selected) androidx.compose.foundation.BorderStroke(1.dp, OrbitCyan) else null
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(dayNumber.toString(), color = if (selected) Color.White else OrbitText, fontSize = 13.sp, fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal)
                                            if (count > 0) Text("• $count", color = if (selected) Color.White else OrbitCyan, fontSize = 9.sp)
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f).height(48.dp))
                                }
                            }
                        }
                    }
                    Text("Toque em um dia para ver os compromissos. Os pontos indicam dias com agendamentos.", color = OrbitMuted, fontSize = 10.sp)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(OrbitTaskTime.displayDate(selectedDate), color = OrbitText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Compromissos do dia selecionado", color = OrbitMuted, fontSize = 11.sp)
                }
                Text("${dayTasks.size} item(ns)", color = OrbitCyan, fontSize = 11.sp)
            }
        }
        if (dayTasks.isEmpty()) item { EmptyState("Nenhum compromisso neste dia", "Escolha outra data ou adicione um novo agendamento.", Icons.Default.EventAvailable) }
        items(dayTasks, key = { it.id }) { TaskRow(it, {}, null, false) }
    }
}

@Composable
private fun FilesScreen(documents: List<OrbitDocument>, onAdd: () -> Unit, onDelete: (OrbitDocument) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Seus arquivos", color = OrbitText, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("Guarde recibos, comprovantes, garantias e documentos importantes.", color = OrbitMuted, fontSize = 13.sp) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel2), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Icon(Icons.Default.FolderOpen, null, tint = OrbitCyan, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(10.dp))
                    Text("Central de documentos", color = OrbitText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Selecione arquivos do seu dispositivo para mantê-los listados no ORBIT.", color = OrbitMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = OrbitBlue)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Adicionar arquivo") }
                }
            }
        }
        item { SectionHeader("Arquivos salvos", "${documents.size} itens") }
        if (documents.isEmpty()) item { EmptyState("Nenhum arquivo adicionado", "Seus arquivos aparecerão aqui depois de selecionados.", Icons.Default.Description) }
        items(documents, key = { it.uri }) { doc ->
            Card(
                onClick = {
                    try {
                        val uri = Uri.parse(doc.uri)
                        val mime = context.contentResolver.getType(uri) ?: "*/*"
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "Abrir documento"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Não foi possível abrir este arquivo. Verifique se o aplicativo correspondente está instalado.", Toast.LENGTH_LONG).show()
                    }
                },
                colors = CardDefaults.cardColors(containerColor = OrbitPanel),
                shape = RoundedCornerShape(15.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = OrbitCyan, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(doc.name, color = OrbitText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Toque para abrir", color = OrbitMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { onDelete(doc) }) { Icon(Icons.Default.DeleteOutline, "Remover", tint = OrbitMuted) }
                }
            }
        }
        item { Text("Os arquivos permanecem no local original do dispositivo. O ORBIT guarda uma referência para encontrá-los; não faz cópia para a nuvem.", color = OrbitMuted, fontSize = 11.sp) }
    }
}

@Composable
private fun TaskRow(task: OrbitTask, onToggle: (OrbitTask) -> Unit, onDelete: ((OrbitTask) -> Unit)?, showToggle: Boolean = true, onEdit: ((OrbitTask) -> Unit)? = null) {
    val overdue = OrbitTaskTime.isOverdue(task)
    Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showToggle) Checkbox(task.done, { onToggle(task) }, colors = CheckboxDefaults.colors(checkedColor = OrbitGreen, uncheckedColor = OrbitMuted))
            else Icon(Icons.Default.Event, null, tint = OrbitCyan, modifier = Modifier.padding(horizontal = 12.dp))
            Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                Text(task.title, color = if (task.done) OrbitMuted else OrbitText, fontWeight = FontWeight.SemiBold)
                if (task.dueDate.isNotBlank()) Text("Data: ${OrbitTaskTime.displayDate(task.dueDate)} • ${task.reminderTime}" + if (task.reminderLeadMinutes > 0) " • aviso ${task.reminderLeadMinutes} min antes" else " • aviso no horário", color = OrbitMuted, fontSize = 11.sp)
                if (overdue) Text("Atrasada — precisa de atenção", color = Color(0xFFFF8A80), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            if (onEdit != null) IconButton(onClick = { onEdit(task) }) { Icon(Icons.Default.Edit, "Editar tarefa", tint = OrbitCyan) }
            if (onDelete != null) IconButton(onClick = { onDelete(task) }) { Icon(Icons.Default.DeleteOutline, "Excluir tarefa", tint = OrbitMuted) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    initialTask: OrbitTask? = null,
    onSave: (String, String, String) -> Unit
) {
    var title by remember(initialTask?.id) { mutableStateOf(initialTask?.title.orEmpty()) }
    var date by remember(initialTask?.id) { mutableStateOf(initialTask?.dueDate.orEmpty()) }
    var time by remember(initialTask?.id) { mutableStateOf(initialTask?.reminderTime ?: "09:00") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val validTime = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(time.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTask == null) "Nova tarefa" else "Editar tarefa", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("O que precisa fazer?") }, singleLine = true)
                OutlinedTextField(
                    value = if (date.isBlank()) "" else date,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data da tarefa") },
                    placeholder = { Text("Selecionar data") },
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, "Selecionar data") } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Hora do lembrete (24 horas)") },
                    placeholder = { Text("09:00") },
                    singleLine = true,
                    isError = !validTime,
                    trailingIcon = { IconButton(onClick = { showTimePicker = true }) { Icon(Icons.Default.Schedule, "Selecionar horário") } }
                )
                Text("O lembrete será programado para a data e hora escolhidas. O Android pode atrasar avisos por economia de bateria.", color = OrbitMuted, fontSize = 11.sp)
                if (date.isBlank()) Text("Selecione uma data para ativar o lembrete.", color = OrbitMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank() && validTime) onSave(title, date, time) }, enabled = title.isNotBlank() && validTime) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = OrbitPanel, titleContentColor = OrbitText, textContentColor = OrbitText
    )
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = parseOrbitDateMillis(date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { date = formatOrbitDateMillis(it) }
                    showDatePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dateState, showModeToggle = false) }
    }
    if (showTimePicker) {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Horário do lembrete") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(Locale.US, timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") } },
            containerColor = OrbitPanel, titleContentColor = OrbitText, textContentColor = OrbitText
        )
    }
}

private fun parseOrbitDateMillis(value: String): Long? {
    if (value.isBlank()) return null
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val position = java.text.ParsePosition(0)
    val parsed = formatter.parse(value, position)
    return if (parsed != null && position.index == value.length) parsed.time else null
}

private fun formatOrbitDateMillis(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date(value))

@Composable
private fun AssistantDialog(
    onDismiss: () -> Unit,
    onCreateTask: (String, String, String, Int) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf("Olá! Escreva um compromisso com data e horário. Eu identifico as informações e programo o lembrete.")
    }
    var proposedSchedule by remember { mutableStateOf<OrbitScheduleDraft?>(null) }
    val conversation = remember { OrbitScheduleConversation() }

    fun processPrompt() {
        val question = prompt.trim()
        if (question.isBlank()) return

        val result = conversation.submit(question)
        proposedSchedule = result.draft
        answer = if (result.draft != null) {
            val draft = result.draft
            val reminderDescription = if (draft.reminderLeadMinutes > 0) {
                "aviso " + draft.reminderLeadMinutes + " minutos antes"
            } else {
                "aviso no horário do compromisso"
            }
            "Entendi o compromisso:\n• " + draft.title +
                "\n• " + draft.date + " às " + draft.time +
                "\n• " + reminderDescription +
                "\n\nConfira e confirme para salvar."
        } else result.message
        prompt = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NotificationsActive, null, tint = OrbitCyan, modifier = Modifier.size(34.dp)) },
        title = { Text("Assistente de lembretes", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(color = OrbitBlue.copy(alpha = .13f), shape = RoundedCornerShape(12.dp)) {
                    Text("INTERPRETAÇÃO LOCAL • SEM API", color = OrbitCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
                Text(answer, color = OrbitText, fontSize = 13.sp)
                if (proposedSchedule != null) {
                    Button(
                        onClick = {
                            val draft = proposedSchedule ?: return@Button
                            onCreateTask(draft.title, draft.date, draft.time, draft.reminderLeadMinutes)
                            proposedSchedule = null
                            answer = "COMPROMISSO AGENDADO. A notificação e o aviso falado foram programados."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitBlue)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null)
                        Spacer(Modifier.width(6.dp))
                        Text("CONFIRMAR AGENDAMENTO DO COMPROMISSO")
                    }
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        prompt = it
                        proposedSchedule = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Escreva seu compromisso") },
                    placeholder = { Text("Ex.: Reunião amanhã às 15:30") },
                    minLines = 2,
                    maxLines = 4
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            proposedSchedule = null
                            answer = "Exemplos:\n• Me lembre de estudar amanhã às 19h\n• Reunião sexta-feira às 14:30\n• Consulta 20/11/2026 às 09h, com aviso 30 minutos antes\n\nInforme sempre a data e o horário."
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Exemplos") }
                    Button(
                        onClick = { processPrompt() },
                        enabled = prompt.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Interpretar")
                    }
                }
                Text(
                    "O assistente apenas interpreta compromissos e programa notificações e avisos por voz. Os comandos são processados no aparelho.",
                    color = OrbitMuted,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        containerColor = OrbitPanel,
        titleContentColor = OrbitText,
        textContentColor = OrbitText
    )
}

@Composable
private fun ShortcutCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Icon(icon, null, tint = OrbitCyan, modifier = Modifier.size(27.dp)); Spacer(Modifier.height(13.dp))
            Text(title, color = OrbitText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp)); Text(subtitle, color = OrbitMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
@Composable
private fun MiniMetric(text: String, color: Color) {
    Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(50.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}
@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column { Text(title, color = OrbitText, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = OrbitMuted, fontSize = 11.sp) }
}
@Composable
private fun EmptyState(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = OrbitCyan, modifier = Modifier.size(35.dp)); Spacer(Modifier.height(10.dp))
            Text(title, color = OrbitText, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
            Text(subtitle, color = OrbitMuted, fontSize = 12.sp)
        }
    }
}


private fun scheduleOrbitReminder(context: Context, task: OrbitTask, voiceEnabled: Boolean = true) {
    cancelOrbitReminder(context, task.id)
    if (task.dueDate.isBlank() || task.done) return
    val dueMillis = orbitLocalDateTimeMillis(task.dueDate, task.reminderTime) ?: return
    val nowMillis = System.currentTimeMillis()
    if (dueMillis <= nowMillis) return

    val leadMinutes = task.reminderLeadMinutes.coerceIn(0, 1440)
    val requestedReminderMillis = dueMillis - TimeUnit.MINUTES.toMillis(leadMinutes.toLong())
    val leadAlreadyPassed = leadMinutes > 0 && requestedReminderMillis <= nowMillis
    val triggerMillis = if (leadAlreadyPassed) nowMillis else requestedReminderMillis

    val request = OneTimeWorkRequestBuilder<OrbitReminderWorker>()
        .setInitialDelay((triggerMillis - nowMillis).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        .setInputData(androidx.work.Data.Builder()
            .putString(OrbitReminderWorker.KEY_TITLE, task.title)
            .putLong(OrbitReminderWorker.KEY_TASK_ID, task.id)
            .putInt(OrbitReminderWorker.KEY_LEAD_MINUTES, leadMinutes)
            .putBoolean(OrbitReminderWorker.KEY_LEAD_ALREADY_PASSED, leadAlreadyPassed)
            .putBoolean(OrbitReminderWorker.KEY_VOICE_ENABLED, voiceEnabled)
            .build()).build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "orbit_task_reminder_${task.id}", ExistingWorkPolicy.REPLACE, request
    )
}
private fun cancelOrbitReminder(context: Context, taskId: Long) {
    WorkManager.getInstance(context).cancelUniqueWork("orbit_task_reminder_${taskId}")
}

private data class OrbitNotificationEntry(val id: Long, val title: String, val kind: String, val timestamp: Long)

private fun loadOrbitNotificationHistory(context: Context): List<OrbitNotificationEntry> = try {
    val array = JSONArray(context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE).getString("notification_history", "[]"))
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        OrbitNotificationEntry(item.optLong("id"), item.optString("title"), item.optString("kind"), item.optLong("timestamp"))
    }.sortedByDescending { it.timestamp }
} catch (_: Exception) { emptyList() }

private fun buildOrbitBackup(context: Context): String {
    val prefs = context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE)
    return JSONObject()
        .put("app", "SMART_ORBIT")
        .put("schemaVersion", 1)
        .put("createdAt", System.currentTimeMillis())
        .put("tasks", JSONArray(prefs.getString("tasks", "[]") ?: "[]"))
        .put("notificationHistory", JSONArray(prefs.getString("notification_history", "[]") ?: "[]"))
        .toString(2)
}

private data class OrbitBackupData(val tasks: List<OrbitTask>, val notificationHistory: String)

private fun parseOrbitBackup(raw: String): OrbitBackupData {
    val root = JSONObject(raw)
    require(root.optString("app") == "SMART_ORBIT" && root.optInt("schemaVersion") == 1)
    val array = root.getJSONArray("tasks")
    require(array.length() <= 5000)
    val restored = ArrayList<OrbitTask>()
    val ids = HashSet<Long>()
    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val id = item.getLong("id")
        val title = item.getString("title").trim()
        val date = item.getString("date")
        val time = item.optString("reminderTime", "09:00")
        require(id > 0 && ids.add(id) && title.isNotBlank() && title.length <= 180)
        require(orbitLocalDateTimeMillis(date, time) != null) { "Data ou horário inválido no backup" }
        restored.add(OrbitTask(id, title, date, item.optBoolean("done"), time, item.optInt("reminderLeadMinutes", 0).coerceIn(0, 1440)))
    }
    val history = root.optJSONArray("notificationHistory") ?: JSONArray()
    require(history.length() <= 100)
    return OrbitBackupData(restored, history.toString())
}

@Composable
private fun NotificationHistoryScreen(entries: List<OrbitNotificationEntry>, onClear: () -> Unit, onBack: () -> Unit) {
    var cleared by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = OrbitCyan) }
                Column {
                    Text("Histórico de notificações", color = OrbitText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Últimos 100 lembretes registrados neste aparelho.", color = OrbitMuted, fontSize = 11.sp)
                }
            }
        }
        if (entries.isNotEmpty() && !cleared) item {
            TextButton(onClick = { onClear(); cleared = true }) {
                Icon(Icons.Default.DeleteOutline, null); Text("Limpar histórico")
            }
        }
        if (entries.isEmpty() || cleared) item {
            EmptyState("Nenhuma notificação registrada", "Os lembretes enviados aparecerão aqui.", Icons.Default.NotificationsNone)
        }
        if (!cleared) items(entries, key = { it.id }) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = OrbitCyan, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, color = OrbitText, fontWeight = FontWeight.SemiBold)
                        Text(entry.kind, color = OrbitCyan, fontSize = 10.sp)
                        Text(SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale("pt", "BR")).format(Date(entry.timestamp)), color = OrbitMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun loadTasks(context: Context): List<OrbitTask> = try {
    val array = JSONArray(context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE).getString("tasks", "[]"))
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        OrbitTask(o.getLong("id"), o.getString("title"), o.optString("date"), o.optBoolean("done"), o.optString("reminderTime", "09:00"), o.optInt("reminderLeadMinutes", 0))
    }
} catch (_: Exception) { emptyList() }
private fun saveTasks(context: Context, tasks: List<OrbitTask>) {
    val array = JSONArray()
    tasks.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("date", it.dueDate).put("done", it.done).put("reminderTime", it.reminderTime).put("reminderLeadMinutes", it.reminderLeadMinutes)) }
    context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE).edit().putString("tasks", array.toString()).apply()
}
private fun loadDocuments(context: Context): List<OrbitDocument> = try {
    val array = JSONArray(context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE).getString("documents", "[]"))
    (0 until array.length()).map { i -> val o = array.getJSONObject(i); OrbitDocument(o.getString("uri"), o.getString("name")) }
} catch (_: Exception) { emptyList() }
private fun saveDocuments(context: Context, documents: List<OrbitDocument>) {
    val array = JSONArray()
    documents.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
    context.getSharedPreferences("orbit_data", Context.MODE_PRIVATE).edit().putString("documents", array.toString()).apply()
}
private fun queryDisplayName(context: Context, uri: Uri): String = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: (uri.lastPathSegment ?: "Documento")
} catch (_: Exception) { "Documento" }