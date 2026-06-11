package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Pedido

@Composable
fun CocinaScreenContent(
    pedidosList: List<Pedido>,
    filtroEstado: String,
    onFilterChange: (String) -> Unit,
    onStatusUpdate: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter out historical paid ones to keep the chef screen purely for active cooking actions
    val activeChefTickets = pedidosList.filter { it.estado != "pagado" && it.estado != "entregado" }

    val filteredList = when (filtroEstado) {
        "pendiente" -> activeChefTickets.filter { it.estado == "pendiente" }
        "cocinando" -> activeChefTickets.filter { it.estado == "cocinando" }
        "listo" -> activeChefTickets.filter { it.estado == "listo" }
        else -> activeChefTickets
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Upper indicator and description
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("👨‍🍳", fontSize = 24.sp)
                Column {
                    Text(
                        text = "MONITOR PRINCIPAL DE COCINA Y REPOSTERÍA",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Recibe comandas en tiempo real. Modifica el estado de preparación para despacharlas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Segmented filter tabs inside the kitchen screen for fine-grained status checking
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "todos" to "Todos (${activeChefTickets.size})",
                "pendiente" to "Nuevos (${activeChefTickets.count { it.estado == "pendiente" }})",
                "cocinando" to "En Horno (${activeChefTickets.count { it.estado == "cocinando" }})",
                "listo" to "Listos (${activeChefTickets.count { it.estado == "listo" }})"
            ).forEach { (statusKey, statusLabel) ->
                val selected = filtroEstado == statusKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onFilterChange(statusKey) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Text(
                        text = "¡Excelente trabajo! No hay comandas en este estado.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredList, key = { it.id ?: System.currentTimeMillis() }) { item ->
                    CardCocinaPedido(
                        pedido = item,
                        onStatusUpdate = onStatusUpdate
                    )
                }
            }
        }
    }
}
