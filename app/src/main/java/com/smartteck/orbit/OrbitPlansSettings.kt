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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PlansBg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Voltar") }
                Spacer(Modifier.weight(1f))
                Text("ASSINATURAS", color = PlansCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Text("Escolha seu ORBIT", color = PlansText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("Compare os recursos e escolha o plano ideal para sua organização.", color = PlansMuted, fontSize = 13.sp)
        }
        item {
            PlanCard(
                name = "ORBIT FREE", price = "R$ 0,00", description = "Para começar a organizar sua rotina.",
                features = listOf("Criar tarefas manualmente", "Criar lembretes manualmente", "Definir datas e horários", "Consultar agenda e compromissos"),
                current = activeTier == OrbitPlanTier.FREE, buttonLabel = if (activeTier == OrbitPlanTier.FREE) "PLANO ATUAL" else "SELECIONAR",
                enabled = false, accent = PlansCyan, onClick = {}
            )
        }
        item {
            PlanCard(
                name = "ORBIT PLUS", price = plusPrice ?: "R$ 10,00 / mês", description = "Mais recursos para sua rotina.",
                features = listOf("Todos os recursos do FREE", "Lembretes falados por voz", "Personalização de avisos", "Mais opções de organização", "Histórico ampliado"),
                current = activeTier == OrbitPlanTier.PLUS, buttonLabel = if (activeTier == OrbitPlanTier.PLUS) "PLANO ATUAL" else "ASSINAR",
                enabled = activeTier != OrbitPlanTier.PLUS, accent = PlansCyan, onClick = { onSubscribe(OrbitPlanTier.PLUS) }
            )
        }
        item {
            PlanCard(
                name = "ORBIT MAX", price = maxPrice ?: "R$ 49,90 / mês", description = "A experiência completa do SMART ORBIT.",
                features = listOf("Todos os recursos do PLUS", "Assistente de IA por conversa", "Criar, editar e cancelar agendamentos pelo chat", "Identificação de conflitos de horário", "Planejamento semanal inteligente"),
                current = activeTier == OrbitPlanTier.MAX, buttonLabel = if (activeTier == OrbitPlanTier.MAX) "PLANO ATUAL" else "ASSINAR",
                enabled = activeTier != OrbitPlanTier.MAX, accent = PlansGold, onClick = { onSubscribe(OrbitPlanTier.MAX) }
            )
        }
        item {
            Text(status, color = PlansMuted, fontSize = 11.sp)
            Text("Os preços finais e a disponibilidade da compra dependem dos produtos publicados no Google Play. A assinatura só será ativada após confirmação da loja.", color = PlansMuted, fontSize = 10.sp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(description, color = PlansMuted, fontSize = 11.sp)
                }
                Text(price, color = PlansText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
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
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF071321), disabledContainerColor = PlansPanel2),
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
    onBack: () -> Unit
) {
    val voiceAvailable = activeTier == OrbitPlanTier.PLUS || activeTier == OrbitPlanTier.MAX
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
                    Text(when (activeTier) {
                        OrbitPlanTier.FREE -> "ORBIT FREE"
                        OrbitPlanTier.PLUS -> "ORBIT PLUS"
                        OrbitPlanTier.MAX -> "ORBIT MAX"
                    }, color = PlansText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("FREE: tarefas e lembretes manuais, datas, horários e agenda.", color = PlansMuted, fontSize = 12.sp)
                    Text("PLUS: tudo do FREE, lembretes falados, avisos personalizados e histórico ampliado.", color = PlansMuted, fontSize = 12.sp)
                    Text("MAX: tudo do PLUS, assistente de IA e planejamento inteligente.", color = PlansMuted, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PlansPanel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = PlansCyan)
                        Text("Lembretes por voz", color = PlansText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = voiceEnabled && voiceAvailable,
                            onCheckedChange = onVoiceChanged,
                            enabled = voiceAvailable
                        )
                    }
                    if (voiceAvailable) {
                        Text("Os lembretes poderão ser falados pelo aparelho quando estiverem programados.", color = PlansMuted, fontSize = 11.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = PlansGold)
                            Text("Disponível nos planos ORBIT PLUS e ORBIT MAX. Faça upgrade para ativar.", color = PlansGold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
