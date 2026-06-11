package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.PedidoRepository
import com.example.ui.MeseroScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: PedidoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Inicializar repositorio central de sincronización
        repository = PedidoRepository(applicationContext)

        // 2. Habilitar soporte Edge-to-Edge para insets del notch y gestos táctiles del sistema
        enableEdgeToEdge()

        setContent {
            // Observador del tema manual guardado en SharedPreferences
            val sharedPrefs = remember { getSharedPreferences("rest_flow_prefs", MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(sharedPrefs.getString("user_theme_preference", "system") ?: "system")
            }

            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
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
        if (::repository.isInitialized) {
            repository.refreshPedidos()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiamos los WebSockets activos en segundo plano para liberar recursos de red
        if (::repository.isInitialized) {
            repository.cerrarSocket()
        }
    }
}
