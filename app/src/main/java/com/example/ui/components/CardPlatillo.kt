package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*

@Composable
fun CardPlatillo(
    platillo: MenuPlatillo,
    inventario: List<InventarioItem>,
    onAgregar: () -> Unit,
    tasaCambio: Float,
    onEditar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ingrediente = inventario.find { it.id == platillo.inventarioDependienteId }
    val isAgotado = ingrediente != null && ingrediente.stock <= 0

    Card(
        modifier = modifier
            .testTag("platillo_card_${platillo.nombre}")
            .clickable(enabled = !isAgotado) { onAgregar() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAgotado) Color(0xFFEEEEEE) else Color(0xFFF7F2FA)
        ),
        border = BorderStroke(1.dp, if (isAgotado) Color.LightGray else Color(0xFFCAC4D0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = platillo.emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFE8DEF8))
                            .padding(6.dp)
                    )
                    
                    Column {
                        Text(
                            text = platillo.nombre,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1D1B20)
                        )
                        if (isAgotado) {
                            Text(
                                "AGOTADO",
                                color = Color.Red,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                if (onEditar != null) {
                    IconButton(
                        onClick = onEditar,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Platillo",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$${String.format("%.2f", platillo.precio)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color(0xFF6750A4)
                        )
                    )
                    Text(
                        text = "VES ${String.format("%.2f", platillo.precio * tasaCambio)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF137333)
                        )
                    )
                }
                
                if (platillo.esPorPeso) {
                    Surface(
                        color = Color(0xFFE6E1E5),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Por KG",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = platillo.descripcion,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF49454F),
                maxLines = 2,
                minLines = 2,
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAgregar,
                enabled = !isAgotado,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8DEF8),
                    contentColor = Color(0xFF1D192B)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Añadir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
