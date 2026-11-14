package com.smartteck.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PremiumBg = Color(0xFF10243A)
private val PremiumText = Color(0xFFF4F8FF)
private val PremiumMuted = Color(0xFF9DB0C7)
private val PremiumCyan = Color(0xFF54D6E8)
private val PremiumGold = Color(0xFFFFD166)
private val PremiumBlue = Color(0xFF3188FF)

@Composable
fun OrbitPremiumDialog(
    isPremium: Boolean,
    price: String?,
    status: String,
    productReady: Boolean,
    onRefresh: () -> Unit,
    onPurchase: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WorkspacePremium, null, tint = PremiumGold) },
        title = { Text("SMART ORBIT MAX", color = PremiumText, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = PremiumGold.copy(alpha = .13f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (isPremium) "ASSINATURA ATIVA" else "INTELIGÊNCIA AVANÇADA",
                        color = PremiumGold, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                }
                Text("A experiência mais completa do SMART ORBIT, reunindo lembretes por voz, assistente de IA e acompanhamento avançado da sua rotina.", color = PremiumText, fontSize = 13.sp)
                PremiumFeature("Criar, editar e cancelar compromissos por conversa")
                PremiumFeature("Agendamentos por linguagem natural e recorrência")
                PremiumFeature("Detecção de conflitos de data e horário")
                PremiumFeature("Painel de progresso: hoje, semana, mês e ano")
                PremiumFeature("Todos os recursos avançados do ORBIT MAX, incluindo lembretes por voz")
                Spacer(Modifier.height(2.dp))
                Text(
                    price?.let { "$it / mês, conforme o plano exibido pelo Google Play" }
                        ?: "Preço e período serão exibidos pelo Google Play após a publicação do plano.",
                    color = PremiumCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp
                )
                Text(status, color = PremiumMuted, fontSize = 11.sp)
                if (!isPremium) {
                    Text("A IA só será liberada após a confirmação da assinatura ORBIT MAX. A compra ficará indisponível até a conclusão e validação da IA avançada.", color = PremiumMuted, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPurchase,
                enabled = isPremium.not() && productReady,
                colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue)
            ) { Icon(Icons.Default.Lock, null); Text("Assinar ORBIT MAX") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRefresh) { Text("Atualizar") }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
        containerColor = PremiumBg,
        titleContentColor = PremiumText,
        textContentColor = PremiumText
    )
}

@Composable
private fun PremiumFeature(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CheckCircle, null, tint = PremiumCyan)
        Text(label, color = PremiumText, fontSize = 12.sp)
    }
}
