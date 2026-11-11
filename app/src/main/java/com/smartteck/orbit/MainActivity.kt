package com.smartteck.orbit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val Navy = Color(0xFF071426)
private val Blue = Color(0xFF1677F8)
private val SurfaceBlue = Color(0xFF10243D)
private val Muted = Color(0xFF9CB0C9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OrbitApp() }
    }
}

private enum class Page(val label: String) {
    HOME("Início"), TASKS("Tarefas"), CALENDAR("Agenda"), DOCS("Documentos"), AI("ORBIT AI")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrbitApp() {
    var page by remember { mutableStateOf(Page.HOME) }
    var tasks by remember { mutableStateOf(listOf("Organizar prioridades do dia", "Revisar compromissos")) }
    var showAdd by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, background = Navy, surface = SurfaceBlue,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Navy,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("SMART TECK", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("ORBIT", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { page = Page.AI }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Assistente", tint = Blue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = SurfaceBlue) {
                    val items = listOf(Page.HOME, Page.TASKS, Page.CALENDAR, Page.DOCS)
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = {
                                Icon(
                                    when (item) {
                                        Page.HOME -> Icons.Default.Home
                                        Page.TASKS -> Icons.Default.CheckCircle
                                        Page.CALENDAR -> Icons.Default.CalendarMonth
                                        else -> Icons.Default.Folder
                                    }, contentDescription = item.label
                                )
                            },
                            label = { Text(item.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = Blue,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                if (page == Page.HOME || page == Page.TASKS) {
                    FloatingActionButton(onClick = { showAdd = true }, containerColor = Blue) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar tarefa")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (page) {
                    Page.HOME -> HomePage(tasks, onTasks = { page = Page.TASKS }, onAI = { page = Page.AI })
                    Page.TASKS -> TasksPage(tasks)
                    Page.CALENDAR -> PlaceholderPage("Sua agenda", "Seus compromissos organizados em um só lugar.", Icons.Default.CalendarMonth)
                    Page.DOCS -> PlaceholderPage("Central de documentos", "Recibos, comprovantes, garantias e arquivos importantes.", Icons.Default.Folder)
                    Page.AI -> PlaceholderPage("Assistente ORBIT", "Em breve: envie uma informação e revise as tarefas e datas identificadas antes de salvar.", Icons.Default.AutoAwesome)
                }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Nova tarefa") },
                text = {
                    OutlinedTextField(
                        value = newTask, onValueChange = { newTask = it },
                        label = { Text("O que você precisa fazer?") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newTask.isNotBlank()) tasks = tasks + newTask.trim()
                        newTask = ""
                        showAdd = false
                    }) { Text("Salvar") }
                },
                dismissButton = {
                    TextButton(onClick = { showAdd = false }) { Text("Cancelar") }
                },
                containerColor = SurfaceBlue,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
private fun HomePage(tasks: List<String>, onTasks: () -> Unit, onAI: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
    ) {
        item {
            Text("SEU DIA, SOB CONTROLE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Olá! 👋", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Vamos organizar o que importa hoje.", color = Muted, fontSize = 14.sp)
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Blue)) {
                Column(Modifier.padding(20.dp)) {
                    Text("RESUMO DE HOJE", color = Color.White.copy(alpha = .8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${tasks.size} tarefas na sua lista", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Pequenos passos deixam tudo mais leve.", color = Color.White.copy(alpha = .85f))
                    TextButton(onClick = onTasks) { Text("Ver tarefas  →", color = Color.White) }
                }
            }
        }
        item { SectionTitle("Acesso rápido") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard("Assistente", "Organize informações", Icons.Default.AutoAwesome, Modifier.weight(1f), onAI)
                QuickCard("Tarefas", "Acompanhe pendências", Icons.Default.CheckCircle, Modifier.weight(1f), onTasks)
            }
        }
        item { SectionTitle("Próximos passos") }
        items(tasks.take(4)) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceBlue), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RadioButtonUnchecked, null, tint = Blue)
                    Spacer(Modifier.width(12.dp))
                    Text(task, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TasksPage(tasks: List<String>) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Tarefas", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Tudo o que você precisa acompanhar.", color = Muted)
        Spacer(Modifier.height(18.dp))
        if (tasks.isEmpty()) Text("Nenhuma tarefa ainda. Toque em + para adicionar.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks) { task ->
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceBlue)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RadioButtonUnchecked, null, tint = Blue)
                        Spacer(Modifier.width(12.dp))
                        Text(task, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderPage(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(58.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Surface(color = SurfaceBlue, shape = RoundedCornerShape(50.dp)) {
            Text("EM DESENVOLVIMENTO", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = SurfaceBlue), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Icon(icon, null, tint = Blue, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}
