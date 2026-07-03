package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.*
import kotlinx.coroutines.launch

@Composable
fun StaffLoginScreen(
    onLoginSuccess: (String, String) -> Unit,
    isAuthLoading: Boolean,
    repository: PedidoRepository
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F2FA)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🔥 Fogón Guarotuyero Staff",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6750A4)
                )
                Text(
                    text = "Seleccione su rol",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF49454F),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // BOTÓN MESERO
                Button(
                    onClick = { onLoginSuccess("Mesero General", "mesero") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🤵 MODO MESERO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Toma de pedidos y gestión de mesas", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BOTÓN COCINA
                OutlinedButton(
                    onClick = { onLoginSuccess("Chef de Cocina", "cocinero") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, Color(0xFF6750A4))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧑‍🍳 MODO COCINA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color(0xFF6750A4))
                        Text("Gestión de KDS y preparación", style = MaterialTheme.typography.labelSmall, color = Color(0xFF49454F))
                    }
                }
            }
        }
    }
}
