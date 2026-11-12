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

private enum class OrbitPage(val title: String) { HOME("Início"), TASKS("Tarefas"), AGENDA("Agenda"), FILES("Arquivos"), PLANS("Assinaturas"), SETTINGS("Configurações") }

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
    val voiceAvailable = activeTier == OrbitPlanTier.PLUS || activeTier == OrbitPlanTier.MAX
    val billingManager = remember(context) {
        OrbitBillingManager(context) { tier, status, plus, max, plusIsReady, maxIsReady ->
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
        billingManager.connect()
        onDispose { billingManager.close() }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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

    fun addTask(title: String, date: String, time: String, leadMinutes: Int = 0) {
        val task = OrbitTask(System.currentTimeMillis(), title.trim(), date.trim(), false, time.trim(), leadMinutes.coerceIn(0, 1440))
        tasks = tasks + task
        saveTasks(context, tasks)
        scheduleOrbitReminder(context, task, voiceRemindersEnabled && voiceAvailable)
    }
    fun updateTask(updated: OrbitTask) {
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
        if (activeTier == OrbitPlanTier.MAX) showAssistant = true
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
                if (page !in listOf(OrbitPage.PLANS, OrbitPage.SETTINGS)) {
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
                    OrbitPage.HOME -> HomeScreen(tasks, documents.size, { page = OrbitPage.TASKS }, { page = OrbitPage.AGENDA }, openAssistant, { page = OrbitPage.PLANS }, { updateTask(it.copy(done = !it.done)) })
                    OrbitPage.TASKS -> TasksScreen(tasks, { updateTask(it.copy(done = !it.done)) }, { removeTask(it) }, { editingTask = it })
                    OrbitPage.AGENDA -> AgendaScreen(tasks)
                    OrbitPage.FILES -> FilesScreen(documents, { documentPicker.launch(arrayOf("*/*")) }, {
                        documents = documents.filterNot { doc -> doc.uri == it.uri }; saveDocuments(context, documents)
                    })
                    OrbitPage.PLANS -> OrbitPlansScreen(
                        activeTier, plusPrice, maxPrice, plusReady, maxReady, premiumStatus,
                        onSubscribe = { tier ->
                            val activity = context as? Activity
                            if (activity != null) billingManager.launchPurchase(activity, tier)
                            else Toast.makeText(context, "Não foi possível abrir o Google Play.", Toast.LENGTH_SHORT).show()
                        },
                        onBack = { page = OrbitPage.HOME }
                    )
                    OrbitPage.SETTINGS -> OrbitSettingsScreen(
                        activeTier, voiceRemindersEnabled,
                        onVoiceChanged = { enabled ->
                            if (voiceAvailable) {
                                voiceRemindersEnabled = enabled
                                context.getSharedPreferences("orbit_settings", Context.MODE_PRIVATE).edit().putBoolean("voice_reminders", enabled).apply()
                                Toast.makeText(context, if (enabled) "Lembretes por voz ativados" else "Lembretes por voz desativados", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBack = { page = OrbitPage.HOME }
                    )
                }
            }
        }
        if (showTaskDialog || editingTask != null) {
            val taskBeingEdited = editingTask
            EnhancedAddTaskDialog(
                onDismiss = { showTaskDialog = false; editingTask = null },
                initialTask = taskBeingEdited
            ) { title, date, time ->
                if (taskBeingEdited == null) addTask(title, date, time)
                else updateTask(taskBeingEdited.copy(title = title.trim(), dueDate = date.trim(), reminderTime = time))
                showTaskDialog = false
                editingTask = null
            }
        }
        if (showAssistant && activeTier == OrbitPlanTier.MAX) EnhancedAssistantDialog(
            onDismiss = { showAssistant = false },
            onCreateTask = { title, date, time, lead -> addTask(title, date, time, lead) }
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
private fun HomeScreen(tasks: List<OrbitTask>, documentCount: Int, onTasks: () -> Unit, onAgenda: () -> Unit, onAssistant: () -> Unit, onPlans: () -> Unit, onToggle: (OrbitTask) -> Unit) {
    val pending = tasks.count { !it.done }
    val completed = tasks.count { it.done }
    val today = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlans, colors = ButtonDefaults.buttonColors(containerColor = OrbitBlue), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Assinaturas")
                }
            }
            Text(today.replaceFirstChar { it.uppercase() }, color = OrbitMuted, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text("Seu mundo, em ordem.", color = OrbitText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Tudo o que importa, em um só lugar.", color = OrbitMuted, fontSize = 14.sp)
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = OrbitPanel2)) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.align(Alignment.TopEnd).size(145.dp).background(OrbitBlue.copy(alpha = .12f), CircleShape))
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = OrbitBlue.copy(alpha = .22f), shape = RoundedCornerShape(50.dp)) {
                                Text("PAINEL DE CONTROLE", color = OrbitCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                            }
                            Spacer(Modifier.weight(1f)); Icon(Icons.Default.AutoAwesome, null, tint = OrbitCyan)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("$pending", color = OrbitText, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                        Text("tarefas pendentes", color = OrbitMuted, fontSize = 14.sp)
                        Spacer(Modifier.height(17.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            MiniMetric("$completed concluídas", OrbitGreen)
                            MiniMetric("$documentCount arquivos", OrbitCyan)
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onTasks, colors = ButtonDefaults.buttonColors(containerColor = OrbitBlue), shape = RoundedCornerShape(12.dp)) {
                            Text("Organizar meu dia"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        item { SectionHeader("Acesso rápido", "Atalhos para sua rotina") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard("Assistente", "Transforme informações em ações", Icons.Default.AutoAwesome, Modifier.weight(1f), onAssistant)
                ShortcutCard("Agenda", "Veja seus compromissos", Icons.Default.CalendarMonth, Modifier.weight(1f), onAgenda)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Próximas tarefas", color = OrbitText, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("O que merece sua atenção", color = OrbitMuted, fontSize = 11.sp) }
                Spacer(Modifier.weight(1f)); TextButton(onClick = onTasks) { Text("Ver todas", color = OrbitCyan) }
            }
        }
        if (tasks.none { !it.done }) item { EmptyState("Tudo tranquilo por aqui", "Adicione uma tarefa pelo botão + para começar.", Icons.Default.CheckCircle) }
        else items(
            tasks.filter { !it.done }
                .sortedWith(compareBy<OrbitTask>({ if (it.dueDate.isBlank()) "9999-99-99" else it.dueDate }, { it.reminderTime }))
                .take(4),
            key = { it.id }
        ) { TaskRow(it, onToggle, null) }
    }
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
    val dated = tasks.filter { it.dueDate.isNotBlank() }.sortedBy { it.dueDate }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Sua agenda", color = OrbitText, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("Datas importantes ligadas às suas tarefas.", color = OrbitMuted, fontSize = 13.sp) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel2), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = OrbitCyan, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(14.dp))
                    Column { Text(SimpleDateFormat("d 'de' MMMM", Locale("pt", "BR")).format(Date()), color = OrbitText, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Hoje", color = OrbitMuted, fontSize = 12.sp) }
                }
            }
        }
        item { SectionHeader("Datas das tarefas", "${dated.size} com data definida") }
        if (dated.isEmpty()) item { EmptyState("Agenda livre", "Ao criar uma tarefa, informe a data no formato AAAA-MM-DD para vê-la aqui.", Icons.Default.EventAvailable) }
        items(dated, key = { it.id }) { TaskRow(it, {}, null, false) }
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
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val overdue = !task.done && task.dueDate.isNotBlank() && task.dueDate.length == 10 && task.dueDate < todayIso
    Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showToggle) Checkbox(task.done, { onToggle(task) }, colors = CheckboxDefaults.colors(checkedColor = OrbitGreen, uncheckedColor = OrbitMuted))
            else Icon(Icons.Default.Event, null, tint = OrbitCyan, modifier = Modifier.padding(horizontal = 12.dp))
            Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                Text(task.title, color = if (task.done) OrbitMuted else OrbitText, fontWeight = FontWeight.SemiBold)
                if (task.dueDate.isNotBlank()) Text("Data: ${task.dueDate} • ${task.reminderTime}" + if (task.reminderLeadMinutes > 0) " • aviso ${task.reminderLeadMinutes} min antes" else " • aviso no horário", color = OrbitMuted, fontSize = 11.sp)
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

    fun processPrompt() {
        val question = prompt.trim()
        if (question.isBlank()) return

        proposedSchedule = null
        val draft = OrbitCommandParser.parseSchedule(question)
        if (draft != null) {
            proposedSchedule = draft
            val reminderDescription = if (draft.reminderLeadMinutes > 0) {
                "aviso " + draft.reminderLeadMinutes + " minutos antes"
            } else {
                "aviso no horário do compromisso"
            }
            answer = "Entendi o compromisso:\n• " + draft.title +
                "\n• " + draft.date + " às " + draft.time +
                "\n• " + reminderDescription +
                "\n\nConfira e confirme para salvar."
            prompt = ""
        } else if (OrbitCommandParser.hasSchedulingIntent(question) && !OrbitCommandParser.hasExplicitTime(question)) {
            answer = "por favor defina o horário do lembrete"
        } else if (OrbitCommandParser.hasSchedulingIntent(question)) {
            answer = "Não consegui identificar a data do compromisso. Informe a data e tente novamente."
        } else {
            answer = "Eu cuido somente dos seus compromissos e lembretes. Escreva o que deseja lembrar, incluindo a data e o horário."
        }
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
    // Always replace/cancel the previous request first. This prevents reminders
    // from firing after a task is completed, cleared, or edited to an invalid date.
    cancelOrbitReminder(context, task.id)
    if (task.dueDate.isBlank() || task.done) return
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val position = java.text.ParsePosition(0)
    val parsed = try { parser.parse(task.dueDate, position) } catch (_: Exception) { null }
    if (parsed == null || position.index != task.dueDate.length) return
    val now = Calendar.getInstance()
    val timeParts = task.reminderTime.split(":")
    if (timeParts.size != 2) return
    val hour = timeParts[0].toIntOrNull() ?: return
    val minute = timeParts[1].toIntOrNull() ?: return
    if (hour !in 0..23 || minute !in 0..59) return
    val due = Calendar.getInstance().apply {
        time = parsed
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today = now.get(Calendar.YEAR) == due.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == due.get(Calendar.DAY_OF_YEAR)
    if (due.before(now)) return
    due.add(Calendar.MINUTE, -task.reminderLeadMinutes.coerceIn(0, 1440))
    // If the event is still in the future but its lead-time reminder has
    // already passed, notify immediately instead of silently dropping it.
    val request = OneTimeWorkRequestBuilder<OrbitReminderWorker>()
        .setInitialDelay((due.timeInMillis - now.timeInMillis).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        .setInputData(androidx.work.Data.Builder()
            .putString(OrbitReminderWorker.KEY_TITLE, task.title)
            .putLong(OrbitReminderWorker.KEY_TASK_ID, task.id)
            .putInt(OrbitReminderWorker.KEY_LEAD_MINUTES, task.reminderLeadMinutes)
            .putBoolean(OrbitReminderWorker.KEY_VOICE_ENABLED, voiceEnabled)
            .build()).build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "orbit_task_reminder_${task.id}", ExistingWorkPolicy.REPLACE, request
    )
}
private fun cancelOrbitReminder(context: Context, taskId: Long) {
    WorkManager.getInstance(context).cancelUniqueWork("orbit_task_reminder_${taskId}")
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