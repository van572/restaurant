package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.PrintUtils

@Composable
fun ActiveComandaSummaryBox(
    mesaName: String,
    carrito: List<ItemCart>,
    totalCarrito: Double,
    tasaCambio: Float,
    onEmpty: () -> Unit,
    onNotesClick: (Int, ItemCart) -> Unit,
    onMinusClick: (Int, ItemCart) -> Unit,
    onPlusClick: (Int, ItemCart) -> Unit,
    onEnviarClick: () -> Unit,
    isSending: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart, 
                        contentDescription = null, 
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Comanda: $mesaName",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1D1B20)
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFCAC4D0).copy(alpha = 0.5f))

            if (carrito.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sin platillos agregados aún.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onEmpty,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8DEF8),
                            contentColor = Color(0xFF1D192B)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Explorar Menú", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    carrito.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.platillo.emoji} ${item.platillo.nombre}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF1D1B20)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Subtotal: $${String.format("%.2f", item.platillo.precio * item.cantidad)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF49454F)
                                    )
                                    if (item.notas.isNotEmpty()) {
                                        Text(
                                            text = "📝 *${item.notas}*",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFD97706),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Quantitative modifiers plus notes customization trigger
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Notes edit trigger
                                IconButton(
                                    onClick = { onNotesClick(idx, item) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar notas",
                                        tint = if (item.notas.isNotEmpty()) Color(0xFFD97706) else Color(0xFF6750A4),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Subtract modifier
                                IconButton(
                                    onClick = { onMinusClick(idx, item) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8DEF8))
                                ) {
                                    Text(
                                        text = "−",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D192B)
                                    )
                                }

                                Text(
                                    text = item.cantidad.toString(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF1D1B20),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                // Add modifier
                                IconButton(
                                    onClick = { onPlusClick(idx, item) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8DEF8))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add, 
                                        contentDescription = "Agregar cantidad", 
                                        tint = Color(0xFF1D192B), 
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFCAC4D0).copy(alpha = 0.5f))

                    // Account complete total row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL DE LA CUENTA:", 
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1D1B20)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$${String.format("%.2f", totalCarrito)}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF6750A4)
                                )
                            )
                            Text(
                                text = "VES ${String.format("%.2f", totalCarrito * tasaCambio)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF137333)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val html = PrintUtils.generateReceiptHtml(mesaName, carrito.map { it.toItemPedido() }, totalCarrito, tasaCambio.toDouble())
                                PrintUtils.printTicket(context, html)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Imprimir", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onEnviarClick,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("submit_order_button"),
                            enabled = !isSending && carrito.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp), 
                                    color = Color.White, 
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), 
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Send, null)
                                    Text("ENVIAR PEDIDO A COCINA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
