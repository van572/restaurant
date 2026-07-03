package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*

@Composable
fun CardCocinaPedido(
    pedido: Pedido,
    onStatusUpdate: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (pedido.estado) {
        "pendiente" -> Color(0xFFE65100)
        "cocinando" -> Color(0xFFFBC02D)
        "listo" -> Color(0xFF4CAF50)
        else -> Color.Gray
    }

    val statusLabel = when (pedido.estado) {
        "pendiente" -> "PEDIDO NUEVO 🪵"
        "cocinando" -> "EN HORNO / PREPARANDO 🔥"
        "listo" -> "LISTO PARA SERVIR 🛎️"
        else -> pedido.estado.uppercase()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pedido.mesa,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D1B20)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Text("Mesero: ${pedido.mesero}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            pedido.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${item.cantidad.formatQty()}x ${item.producto}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (item.notas.isNotEmpty()) {
                            Text(
                                text = "👉 NOTA: ${item.notas}",
                                color = Color(0xFFB3261E),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val id = pedido.id
            if (id != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pedido.estado == "pendiente") {
                        Button(
                            onClick = { onStatusUpdate(id, "cocinando") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COCINAR", fontWeight = FontWeight.Bold)
                        }
                    } else if (pedido.estado == "cocinando") {
                        Button(
                            onClick = { onStatusUpdate(id, "listo") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("MARCAR LISTO", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
