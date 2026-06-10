package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.PedidoRepository
import com.example.ui.MeseroScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: PedidoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Inicializar repositorio central de sincronización
        repository = PedidoRepository()

        // 2. Habilitar soporte Edge-to-Edge para insets del notch y gestos táctiles del sistema
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MeseroScreen(
                        repository = repository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refrescar datos en caliente al volver a enfocar la aplicación
        repository.refreshPedidos()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiamos los WebSockets activos en segundo plano para liberar recursos de red
        if (::repository.isInitialized) {
            repository.cerrarSocket()
        }
    }
}
