package com.smartteck.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// FREE representa o uso sem assinatura; ORBIT MAX é a única assinatura paga.
enum class OrbitPlanTier { FREE, PLUS, MAX }

private val PlansBg = Color(0xFF071321)
private val PlansPanel = Color(0xFF10243A)
private val PlansPanel2 = Color(0xFF162E49)
private val PlansBlue = Color(0xFF3188FF)
private val PlansCyan = Color(0xFF54D6E8)
private val PlansText = Color(0xFFF4F8FF)
private val PlansMuted = Color(0xFF9DB0C7)
private val PlansGold = Color(0xFFFFD166)

@Composable
fun OrbitPlansScreen(
    activeTier: OrbitPlanTier,
    plusPrice: String?,
    maxPrice: String?,
    plusReady: Boolean,
    maxReady: Boolean,
    status: String,
    onSubscribe: (OrbitPlanTier) -> Unit,
    onBack: () -> Unit
) {
    val isMax = activeTier == OrbitPlanTier.MAX
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PlansBg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Voltar") }
                Spacer(Modifier.weight(1f))
                Text("ASSINATURA", color = PlansCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Text("Um plano. Tudo liberado.", color = PlansText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("O ORBIT MAX reúne todos os recursos em uma única assinatura.", color = PlansMuted, fontSize = 13.sp)
        }
        item {
            PlanCard(
                name = "ORBIT MAX",
                price = maxPrice ?: "Consulte no Google Play",
                description = "A experiência completa do SMART ORBIT, sem dividir recursos entre planos.",
                features = listOf(
                    "Todos os recursos de tarefas, agenda e calendário",
                    "Lembretes e notificações programadas",
                    "Lembretes por voz, com opção de ativar ou desativar",
                    "Agendamentos recorrentes diários, semanais e mensais",
                    "Detecção de conflitos de data e horário",
                    "Assistente ORBIT MAX para criar compromissos por conversa",
                    "Interpretação de linguagem natural e identificação de informações ausentes",
                    "Criação de recorrências pelo assistente",
                    "Painel inteligente com atrasos, tarefas de hoje e próximo compromisso",
                    "Relatórios de progresso por dia, semana, mês e ano",
                    "Arquivos, histórico e ferramentas de backup"
                ),
                current = isMax,
                buttonLabel = if (isMax) "ASSINATURA ATIVA" else "ASSINAR ORBIT MAX",
                enabled = !isMax && maxReady,
                accent = PlansGold,
                onClick = { onSubscribe(OrbitPlanTier.MAX) }
            )
        }
        item {
            Text(status, color = PlansMuted, fontSize = 11.sp)
            Text(
                "O preço e a disponibilidade dependem do produto ORBIT MAX publicado no Google Play. A assinatura só será ativada após confirmação da loja.",
                color = PlansMuted, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PlanCard(
    name: String, price: String, description: String, features: List<String>,
    current: Boolean, buttonLabel: String, enabled: Boolean, accent: Color, onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PlansPanel)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(description, color = PlansMuted, fontSize = 11.sp)
            Text(price, color = PlansText, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.height(16.dp))
                    Text(feature, color = PlansText, fontSize = 12.sp)
                }
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF071321),
                    disabledContainerColor = PlansPanel2
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text(buttonLabel, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
fun OrbitSettingsScreen(
    activeTier: OrbitPlanTier,
    voiceEnabled: Boolean,
    onVoiceChanged: (Boolean) -> Unit,
    onHistory: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit
) {
    val voiceAvailable = activeTier == OrbitPlanTier.MAX
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PlansBg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Voltar") }
            Text("Configurações", color = PlansText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("Gerencie suas preferências e confira os recursos do seu plano.", color = PlansMuted, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PlansPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("SEU PLANO", color = PlansCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (activeTier == OrbitPlanTier.MAX) "ORBIT MAX" else "SEM ASSINATURA", color = PlansText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (activeTier == OrbitPlanTier.MAX) "Você tem acesso a todos os benefícios do SMART ORBIT."
                        else "Você está usando o acesso básico. Assine o ORBIT MAX para liberar todos os recursos.",
                        color = PlansMuted, fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PlansPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = PlansCyan)
                        Text("Lembretes por voz", color = PlansText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = voiceEnabled && voiceAvailable, onCheckedChange = onVoiceChanged, enabled = voiceAvailable)
                    }
                    if (voiceAvailable) {
                        Text("Os lembretes poderão ser falados pelo aparelho quando estiverem programados.", color = PlansMuted, fontSize = 11.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = PlansGold)
                            Text("Recurso incluído na assinatura ORBIT MAX.", color = PlansGold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PlansPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("DADOS E PRIVACIDADE", color = PlansCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Seu histórico e seus backups ficam armazenados localmente no aparelho.", color = PlansMuted, fontSize = 11.sp)
                    Button(onClick = onHistory, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PlansPanel2)) { Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(8.dp)); Text("Histórico de notificações") }
                    Button(onClick = onBackup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PlansBlue)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Exportar backup") }
                    Button(onClick = onRestore, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PlansPanel2)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Restaurar backup") }
                    Text("O backup inclui tarefas e histórico de notificações. Os arquivos anexados permanecem nos locais originais do dispositivo.", color = PlansMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
