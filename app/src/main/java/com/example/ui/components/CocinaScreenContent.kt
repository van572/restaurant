package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*

@Composable
fun CocinaScreenContent(
    pedidos: List<Pedido>,
    filtro: String,
    onFiltroChange: (String) -> Unit,
    onStatusUpdate: (Long, String) -> Unit,
    onRefresh: () -> Unit
) {
    val filtered = when (filtro) {
        "pendientes" -> pedidos.filter { it.estado == "pendiente" || it.estado == "cocinando" }
        "listos" -> pedidos.filter { it.estado == "listo" }
        else -> pedidos
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Monitor de Cocina (KDS)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("${filtered.size} órdenes activas", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
            }
        }

        TabRow(
            selectedTabIndex = when (filtro) {
                "pendientes" -> 0
                "listos" -> 1
                else -> 2
            },
            containerColor = Color.Transparent,
            contentColor = Color(0xFF6750A4)
        ) {
            Tab(selected = filtro == "pendientes", onClick = { onFiltroChange("pendientes") }) {
                Text("🔥 PENDIENTES", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Tab(selected = filtro == "listos", onClick = { onFiltroChange("listos") }) {
                Text("🛎️ LISTOS", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Tab(selected = filtro == "todos", onClick = { onFiltroChange("todos") }) {
                Text("📋 TODOS", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay pedidos en esta categoría.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filtered) { pedido ->
                    CardCocinaPedido(
                        pedido = pedido,
                        onStatusUpdate = onStatusUpdate
                    )
                }
            }
        }
    }
}
