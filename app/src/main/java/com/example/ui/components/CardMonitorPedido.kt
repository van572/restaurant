package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*

@Composable
fun CardMonitorPedido(
    pedido: Pedido,
    onStatusUpdate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusProps = when (pedido.estado) {
        "pendiente" -> Triple(Color(0xFFB3261E), Color(0xFFEFB8C8).copy(alpha = 0.2f), "📝 PENDIENTE")
        "cocinando" -> Triple(Color(0xFFF59E0B), Color(0xFFFFF7E6), "🔥 COCINANDO")
        "listo" -> Triple(Color(0xFF1A73E8), Color(0xFFE8DEF8), "🛎️ ¡LISTO PARA SERVIR!")
        "entregado" -> Triple(Color(0xFF6750A4), Color(0xFFE8DEF8).copy(alpha = 0.5f), "🍽️ SERVIDO")
        else -> Triple(Color.Gray, Color(0xFFF7F2FA), pedido.estado.uppercase())
    }

    val (colorEstatus, bgBadge, textEstatus) = statusProps
    val isReady = pedido.estado == "listo"
    val borderStroke = if (isReady) BorderStroke(2.dp, Color(0xFF1A73E8)) else BorderStroke(1.dp, Color(0xFFCAC4D0))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(borderStroke, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) Color(0xFFFEF7FF) else Color(0xFFF7F2FA)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = pedido.mesa,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1D1B20),
                        modifier = Modifier
                            .background(Color(0xFFE8DEF8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "ID: #${pedido.id ?: "..."}",
                        fontSize = 12.sp,
                        color = Color(0xFF49454F)
                    )
                }

                Surface(
                    color = bgBadge,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colorEstatus)
                ) {
                    Text(
                        text = textEstatus,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorEstatus,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val itemsString = pedido.items.joinToString(", ") { "${it.cantidad}x ${it.producto}" }
            Text(
                text = "Platillos: $itemsString",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1D1B20),
                fontWeight = FontWeight.Medium
            )

            if (isReady) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8DEF8), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications, 
                        contentDescription = null, 
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "La cocina reporta orden lista. Acude a despacharla.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A73E8)
                    )
                }
            }

            val id = pedido.id
            if (id != null && onStatusUpdate != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (pedido.estado) {
                        "pendiente" -> {
                            Button(
                                onClick = { onStatusUpdate("cocinando") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Empezar Cocina", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "cocinando" -> {
                            Button(
                                onClick = { onStatusUpdate("listo") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Listo para Servir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "listo" -> {
                            Button(
                                onClick = { onStatusUpdate("entregado") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Marcar Entregado", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "entregado" -> {
                            Button(
                                onClick = { onStatusUpdate("pagado") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF137333)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pagado / Archivar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
