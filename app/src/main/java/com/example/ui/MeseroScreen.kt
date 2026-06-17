package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectionType
import com.example.data.ItemPedido
import com.example.data.Pedido
import com.example.data.PedidoRepository

import kotlinx.serialization.Serializable

// Presets Estándar del Menú para Selección por el Mesero
// Presets Estándar del Menú para Selección por el Mesero
val MENU_ITEMS = listOf(
    MenuPlatillo("Hamburguesa Premium", 12.50, CategoriaPlatillo.COMIDA, "Queso cheddar, tocino, aderezo gourmet.", "🍔"),
    MenuPlatillo("Pizza Personal Pepperoni", 15.00, CategoriaPlatillo.COMIDA, "Salsa de la casa, pepperoni, mozzarella.", "🍕"),
    MenuPlatillo("Tacos de Res (x3)", 8.50, CategoriaPlatillo.COMIDA, "Cebollitas asadas, cilantro, salsas.", "🌮"),
    MenuPlatillo("Alitas BBQ", 9.50, CategoriaPlatillo.COMIDA, "10 piezas de alitas bañadas en salsa barbacoa.", "🍗"),
    MenuPlatillo("Papas Fritas", 4.00, CategoriaPlatillo.ACOMPANAMIENTO, "Doraditas y crujientes con sal marina.", "🍟"),
    MenuPlatillo("Té Frío Limón", 3.00, CategoriaPlatillo.BEBIDA, "Infusión de té negro con zumo fresco.", "🍹"),
    MenuPlatillo("Refresco Sabor Cola", 2.50, CategoriaPlatillo.BEBIDA, "Vaso grande con hielo y limón.", "🥤"),
    MenuPlatillo("Agua Mineral", 2.00, CategoriaPlatillo.BEBIDA, "Agua gasificada purificada fría.", "💧")
)

fun saveMenuToPrefs(sharedPrefs: android.content.SharedPreferences, list: List<MenuPlatillo>) {
    val serialized = list.joinToString("###") { p ->
        "${p.nombre}||${p.precio}||${p.categoria.name}||${p.descripcion}||${p.emoji}"
    }
    sharedPrefs.edit().putString("custom_menu_items", serialized).apply()
}

fun loadMenuFromPrefs(sharedPrefs: android.content.SharedPreferences): List<MenuPlatillo>? {
    val raw = sharedPrefs.getString("custom_menu_items", null) ?: return null
    if (raw.trim().isEmpty()) return emptyList()
    return try {
        raw.split("###").map { item ->
            val parts = item.split("||")
            MenuPlatillo(
                nombre = parts[0],
                precio = parts[1].toDouble(),
                categoria = CategoriaPlatillo.valueOf(parts[2]),
                descripcion = if (parts.size > 3) parts[3] else "",
                emoji = if (parts.size > 4) parts[4] else "🍔"
            )
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeseroScreen(
    repository: PedidoRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Observe StateFlows del Repositorio
    val pedidosState by repository.pedidos.collectAsState()
    val connectionType by repository.connectionState.collectAsState()
    val isConnectingWS by repository.isConnectingWS.collectAsState()

    // Estados Locales de UI y Persistencia básica de Sesión del Mesero (Entrando por primera vez)
    val sharedPrefs = remember { context.getSharedPreferences("rest_flow_prefs", android.content.Context.MODE_PRIVATE) }
    var meseroNombre by remember { 
        mutableStateOf(sharedPrefs.getString("mesero_nombre", "") ?: "") 
    }
    var showWelcomeDialog by remember { mutableStateOf(meseroNombre.isBlank()) }
    var welcomeNombreTemp by remember { mutableStateOf("") }
    var mesaSeleccionada by remember { mutableStateOf("Mesa 1") }
    var categoriaSeleccionada by remember { mutableStateOf(CategoriaPlatillo.COMIDA) }
    
    // --- CHEF / KITCHEN STATE CONFIGS ---
    var userRole by remember { mutableStateOf("mesero") } // "mesero" o "cocinero"
    var lastKnownMaxId by remember { mutableStateOf<Long?>(null) }
    var activeNewOrderNotification by remember { mutableStateOf<Pedido?>(null) }
    var cocineroFiltroEstado by remember { mutableStateOf("todos") } // "todos", "pendiente", "cocinando", "listo"
    
    // El "Carrito" o Pedido en curso
    val carrito = remember { mutableStateListOf<ItemCart>() }
    
    // Estado de envío
    var isSending by remember { mutableStateOf(false) }

    // Diálogos y modales
    var showNotesDialog by remember { mutableStateOf(false) }
    var activeNotesCartIndex by remember { mutableStateOf(-1) }
    var notesTextTemp by remember { mutableStateOf("") }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showConfirmOrderDialog by remember { mutableStateOf(false) }

    // Menú dinámico editable / modificable cargado de SharedPreferences
    val menuPlatillos = remember {
        mutableStateListOf<MenuPlatillo>().apply {
            val loaded = loadMenuFromPrefs(sharedPrefs)
            if (loaded != null) {
                addAll(loaded)
            } else {
                addAll(MENU_ITEMS)
            }
        }
    }

    // --- SINCRONIZACIÓN DEL MENÚ CON LA NUBE ---
    LaunchedEffect(Unit) {
        if (repository.isSupabaseConfigured) {
            repository.fetchDynamicMenu { cloudMenu ->
                if (cloudMenu != null && cloudMenu.isNotEmpty()) {
                    menuPlatillos.clear()
                    menuPlatillos.addAll(cloudMenu)
                    saveMenuToPrefs(sharedPrefs, cloudMenu)
                }
            }
        }
    }

    var showEditPlatilloDialog by remember { mutableStateOf(false) }
    var editingPlatillo by remember { mutableStateOf<MenuPlatillo?>(null) }
    var platilloNombreTemp by remember { mutableStateOf("") }
    var platilloPrecioTemp by remember { mutableStateOf("") }
    var platilloCategoriaTemp by remember { mutableStateOf(CategoriaPlatillo.COMIDA) }
    var platilloDescripcionTemp by remember { mutableStateOf("") }
    var platilloEmojiTemp by remember { mutableStateOf("🍔") }

    // Active View Tab ("mesas", "menu", "pedidos") matching the HTML style
    var activeTab by remember { mutableStateOf("mesas") }

    // Lista de Mesas de Restaurante
    val listMesas = listOf("Mesa 1", "Mesa 2", "Mesa 3", "Mesa 4", "Mesa 5", "Mesa 6", "Mesa 7", "Mesa 8")

    // Calcular Totales
    val totalCarrito = carrito.sumOf { it.platillo.precio * it.cantidad }

    // Extraer Initials para el avatar redondo de la cabecera
    val initials = remember(meseroNombre) {
        meseroNombre.split(" ")
            .filter { it.isNotEmpty() }
            .map { it.first().uppercase() }
            .take(2)
            .joinToString("")
            .let { if (it.isEmpty()) "SC" else it }
    }

    // Launcher para solicitar el permiso de notificaciones en Android 13+ (API 33+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "🔔 Alertas e hilos de cocina activados", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Solicitar permiso automáticamente cuando entren a vista de cocina o cargue
    LaunchedEffect(userRole) {
        if (userRole == "cocinero") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Monitoreo en tiempo real de nuevos pedidos
    LaunchedEffect(pedidosState) {
        if (pedidosState.isNotEmpty()) {
            val currentMaxId = pedidosState.mapNotNull { it.id }.maxOrNull() ?: 0L
            
            if (lastKnownMaxId == null) {
                // Inicialización al arrancar: recordar ID actual sin alertar viejos
                lastKnownMaxId = currentMaxId
            } else if (currentMaxId > lastKnownMaxId!!) {
                // Hay nuevos pedidos creados! Busquemos si hay pendientes creados recientemente
                val nuevoPendiente = pedidosState.find { 
                    it.estado == "pendiente" && (it.id ?: 0L) > lastKnownMaxId!! 
                }
                
                if (nuevoPendiente != null) {
                    activeNewOrderNotification = nuevoPendiente
                    
                    // 1. Emitir tono acústico (BEEP) de comandas estilo impresora térmica
                    try {
                        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 350)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    // 2. Levantar una notificación nativa del sistema
                    try {
                        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
                        val channelId = "kds_alerts_channel"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val channel = NotificationChannel(
                                channelId,
                                "Alertas de Cocina",
                                NotificationManager.IMPORTANCE_HIGH
                            ).apply {
                                description = "Notificaciones urgentes de pedidos nuevos"
                                enableLights(true)
                                enableVibration(true)
                            }
                            notificationManager.createNotificationChannel(channel)
                        }

                        val itemsSummary = nuevoPendiente.items.joinToString(", ") { "${it.cantidad}x ${it.producto}" }
                        val builder = NotificationCompat.Builder(context, channelId)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("🧑‍🍳 ¡Nuevo Pedido en ${nuevoPendiente.mesa}!")
                            .setContentText(itemsSummary)
                            .setStyle(NotificationCompat.BigTextStyle().bigText("Pedido #${nuevoPendiente.id} de la ${nuevoPendiente.mesa}\nPlatillos: $itemsSummary\nAtendido por: ${nuevoPendiente.mesero}"))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setDefaults(NotificationCompat.DEFAULT_ALL)
                            .setAutoCancel(true)

                        notificationManager.notify((nuevoPendiente.id ?: System.currentTimeMillis()).toInt(), builder.build())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                // Actualizar max id
                lastKnownMaxId = currentMaxId
            }
        } else if (lastKnownMaxId == null) {
            lastKnownMaxId = 0L
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Top App Bar styled matching top header element of Vibrant Palette HTML
            Surface(
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile/Mesero Avatar block left side
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showProfileDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6750A4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Column {
                            Text(
                                text = if (userRole == "cocinero") "COCINERO 🧑‍🍳" else "MESERO 🤵",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = if (userRole == "cocinero") Color(0xFF6750A4) else Color(0xFF49454F)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (userRole == "cocinero") "Chef de Cocina" else meseroNombre,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    ),
                                    color = Color(0xFF1D1B20)
                                )
                                if (userRole != "cocinero") {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar nombre",
                                        tint = Color(0xFF49454F),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Connection Action indicators right-side
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Switch Role Pill Button!
                        Surface(
                            color = if (userRole == "cocinero") Color(0xFFE8DEF8) else Color(0xFFF7F2FA),
                            border = BorderStroke(1.dp, if (userRole == "cocinero") Color(0xFF6750A4) else Color(0xFFCAC4D0)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable { 
                                    userRole = if (userRole == "mesero") "cocinero" else "mesero" 
                                }
                                .testTag("toggle_role_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (userRole == "cocinero") "🤵 Ver Mesero" else "🧑‍🍳 Ver Cocina",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (userRole == "cocinero") Color(0xFF6750A4) else Color(0xFF49454F)
                                    )
                                )
                            }
                        }

                        // Conexión State Badge
                        Surface(
                            color = if (connectionType == ConnectionType.NUBE) Color(0xFFE8DEF8) else Color(0xFFFFF7E6),
                            shape = CircleShape,
                            modifier = Modifier.clickable { showProfileDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (connectionType == ConnectionType.NUBE) Color(0xFF137333) else Color(0xFFD97706))
                                )
                                Text(
                                    text = if (connectionType == ConnectionType.NUBE) {
                                        if (isConnectingWS) "Conectando" else "Nube"
                                    } else "Demo",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (connectionType == ConnectionType.NUBE) Color(0xFF1D192B) else Color(0xFF905E00)
                                )
                            }
                        }

                        // Hot refresh button
                        IconButton(
                            onClick = { 
                                repository.refreshPedidos()
                                if (repository.isSupabaseConfigured) {
                                    repository.fetchDynamicMenu { cloudMenu ->
                                        if (cloudMenu != null) {
                                            menuPlatillos.clear()
                                            menuPlatillos.addAll(cloudMenu)
                                            saveMenuToPrefs(sharedPrefs, cloudMenu)
                                        }
                                    }
                                }
                                Toast.makeText(context, "Sincronizando...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF7F2FA))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar",
                                tint = Color(0xFF49454F)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Design HTML-compliant Footer Navigation Bar
            Surface(
                tonalElevation = 8.dp,
                color = Color(0xFFF3EDF7),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                border = BorderStroke(1.dp, Color(0xFFE7E0EC))
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // TAB 1: Inicio (Switches back to tables/menu selection context)
                    val isInicioActive = activeTab == "mesas" || activeTab == "menu"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeTab = "mesas" }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isInicioActive) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Inicio",
                                tint = if (isInicioActive) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Inicio",
                            fontSize = 11.sp,
                            fontWeight = if (isInicioActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isInicioActive) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }

                    // TAB 2: Ordenes (Active KDS)
                    val isOrdenesActive = activeTab == "pedidos"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeTab = "pedidos" }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isOrdenesActive) Color(0xFFE8DEF8) else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Ordenes en Cocina",
                                tint = if (isOrdenesActive) Color(0xFF1D192B) else Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Cocina",
                            fontSize = 11.sp,
                            fontWeight = if (isOrdenesActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isOrdenesActive) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }

                    // TAB 3: Perfil config trigger
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showProfileDialog = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Perfil del Mesero",
                                tint = Color(0xFF49454F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Perfil",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF49454F)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Floating Action Button corresponding to the purple rounded-2xl FAB of Vibrantly Polished HTML
            AnimatedVisibility(
                visible = activeTab != "pedidos" && carrito.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        // Al hacer clic, abrimos directamente el diálogo de confirmación rápida para enviar el pedido a la cocina
                        showConfirmOrderDialog = true
                    },
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("fab_quick_order")
                ) {
                    BadgedBox(
                        badge = {
                            Badge {
                                Text(carrito.sumOf { it.cantidad }.toString())
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart, 
                            contentDescription = "Ver comanda",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFFEF7FF)) // Vibrant background hex
        ) {
            // Heads-up top overlay banner alert for new incoming orders
            AnimatedVisibility(
                visible = activeNewOrderNotification != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                activeNewOrderNotification?.let { ped ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF137333)) // Success color or beautiful dark teal/green representing active order alert
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clickable {
                                activeNewOrderNotification = null
                                userRole = "cocinero"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🛎️", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "¡NUEVO PEDIDO para ${ped.mesa}!",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                val itemsSummary = ped.items.joinToString(", ") { "${it.cantidad}x ${it.producto}" }
                                Text(
                                    text = itemsSummary,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { 
                                    activeNewOrderNotification = null
                                    userRole = "cocinero"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF137333)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Preparar 🧑‍🍳", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { activeNewOrderNotification = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (userRole == "cocinero") {
                CocinaScreenContent(
                    pedidos = pedidosState,
                    filtro = cocineroFiltroEstado,
                    onFiltroChange = { cocineroFiltroEstado = it },
                    onStatusUpdate = { id, nuevoEstado ->
                        repository.actualizarEstadoPedido(id, nuevoEstado) { exito, error ->
                            if (exito) {
                                Toast.makeText(context, "Pedido actualizado a: ${nuevoEstado.uppercase()}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Error: $error ❌", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRefresh = { repository.refreshPedidos() }
                )
            } else {
                // VIEW TABS below header matching:
                // <nav class="px-4 py-2 flex gap-2 overflow-hidden">
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabItems = listOf(
                    "mesas" to "Mesas",
                    "menu" to "Menú",
                    "pedidos" to "Cocina (KDS)"
                )

                tabItems.forEach { (key, label) ->
                    val isSelected = activeTab == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp)) // rounded-2xl matching
                            .background(if (isSelected) Color(0xFFE8DEF8) else Color(0xFFF7F2FA)) // BG tones
                            .clickable { activeTab = key }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = if (isSelected) Color(0xFF1D192B) else Color(0xFF49454F)
                        )
                    }
                }
            }

            Divider(thickness = 1.dp, color = Color(0xFFCAC4D0).copy(alpha = 0.3f))

            // Main Display Logic depending on selected tab
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { targetTab ->
                when (targetTab) {
                    // TAB: MESAS (The Grid of interactive Tables plus Comanda Summary)
                    "mesas" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                        ) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "1. Ubicación: ¿Qué mesa ordenará?",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Color(0xFF1D1B20)
                                        ),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = "Selecciona una mesa para cambiar o consultar comandas en cocina",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF49454F)
                                    )
                                }
                            }

                            // 2-Column Table Cards Grid styled exactly like the Design HTML!
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listMesas.chunked(2).forEach { rowMesas ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowMesas.forEach { mesa ->
                                                // Dynamic live database queries mapping status
                                                val mesaOrders = pedidosState.filter { 
                                                    it.mesa.equals(mesa, ignoreCase = true) && 
                                                    it.estado != "pagado"
                                                }
                                                val hasActiveOrders = mesaOrders.isNotEmpty()
                                                val isReadyToServe = mesaOrders.any { it.estado == "listo" }

                                                // Color dot codes:
                                                // - Pedido listo: Pink dot color `#EFB8C8` or green
                                                // - Ocupada en cocina: Red dot color `#B3261E`
                                                // - Disponible: Blue dot color `#1A73E8`
                                                val dotColor = when {
                                                    isReadyToServe -> Color(0xFFEFB8C8)
                                                    hasActiveOrders -> Color(0xFFB3261E)
                                                    else -> Color(0xFF1A73E8)
                                                }

                                                val textEstatus = when {
                                                    isReadyToServe -> "¡Listo para Servir!"
                                                    hasActiveOrders -> "Ocupada (Cocinando)"
                                                    else -> "Disponible"
                                                }

                                                val totalMesaText = if (hasActiveOrders) {
                                                    "$${String.format("%.2f", mesaOrders.sumOf { it.total })}"
                                                } else {
                                                    "--"
                                                }

                                                val isThisSelectedConfig = mesa == mesaSeleccionada

                                                // Clean Table Card following HTML look
                                                Card(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .border(
                                                            width = if (isThisSelectedConfig) 2.dp else 1.dp,
                                                            color = if (isThisSelectedConfig) Color(0xFF6750A4) else Color(0xFFCAC4D0),
                                                            shape = RoundedCornerShape(16.dp)
                                                        )
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable {
                                                            mesaSeleccionada = mesa
                                                            // Auto shortcut view switch to start adding dishes!
                                                            activeTab = "menu"
                                                            Toast.makeText(context, "$mesa seleccionada para ordenar", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .testTag("mesa_card_$mesa"),
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isThisSelectedConfig) Color(0xFFEADDFF) else Color(0xFFF7F2FA)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(14.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = mesa,
                                                                style = MaterialTheme.typography.titleLarge.copy(
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    fontSize = 20.sp
                                                                ),
                                                                color = Color(0xFF1D1B20)
                                                            )

                                                            // Circular color status dot
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(12.dp)
                                                                    .clip(CircleShape)
                                                                    .background(dotColor)
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        Text(
                                                            text = "Estado",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF49454F),
                                                            modifier = Modifier.alpha(0.7f)
                                                        )

                                                        Text(
                                                            text = textEstatus,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                            color = Color(0xFF1D1B20),
                                                            maxLines = 1
                                                        )

                                                        Spacer(modifier = Modifier.height(6.dp))

                                                        Text(
                                                            text = totalMesaText,
                                                            style = MaterialTheme.typography.titleMedium.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF6750A4)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Active mesa config summary details
                            item {
                                ActiveComandaSummaryBox(
                                    mesaName = mesaSeleccionada,
                                    carrito = carrito,
                                    totalCarrito = totalCarrito,
                                    onEmpty = { activeTab = "menu" },
                                    onNotesClick = { idx, item ->
                                        activeNotesCartIndex = idx
                                        notesTextTemp = item.notas
                                        showNotesDialog = true
                                    },
                                    onMinusClick = { idx, item ->
                                        if (item.cantidad > 1) {
                                            carrito[idx] = item.copy(cantidad = item.cantidad - 1)
                                        } else {
                                            carrito.removeAt(idx)
                                        }
                                    },
                                    onPlusClick = { idx, item ->
                                        carrito[idx] = item.copy(cantidad = item.cantidad + 1)
                                    },
                                    onEnviarClick = {
                                        isSending = true
                                        val itemsComanda = carrito.map { it.toItemPedido() }
                                        val comandaObj = Pedido(
                                            mesa = mesaSeleccionada,
                                            mesero = meseroNombre,
                                            items = itemsComanda,
                                            total = totalCarrito
                                        )
                                        repository.crearPedido(comandaObj) { exito, errorMsg ->
                                            isSending = false
                                            if (exito) {
                                                carrito.clear()
                                                activeTab = "pedidos" // Auto-redirigir a cocina para monitorear el pedido enviado
                                                Toast.makeText(context, "¡Comanda enviada a Cocina! 🍳🚀", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Error: $errorMsg ❌", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    isSending = isSending
                                )
                            }
                        }
                    }

                    // TAB: MENU (Browse platillos classified dynamically and check out comanda)
                    "menu" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "2. Agregar a la ${mesaSeleccionada}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1D1B20)
                                        )
                                        Text(
                                            text = "Seleccionador de platillos del menú",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF49454F)
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Small clean helper link to change Table selection
                                        TextButton(
                                            onClick = { activeTab = "mesas" },
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp))
                                                Text("Mesa", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    menuPlatillos.clear()
                                                    menuPlatillos.addAll(MENU_ITEMS)
                                                    saveMenuToPrefs(sharedPrefs, menuPlatillos)
                                                    Toast.makeText(context, "Menú restablecido a predefinidos", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Color(0xFF4F378B).copy(alpha = 0.1f),
                                                    contentColor = Color(0xFF4F378B)
                                                ),
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Reset", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    editingPlatillo = null // New
                                                    platilloNombreTemp = ""
                                                    platilloPrecioTemp = ""
                                                    platilloCategoriaTemp = categoriaSeleccionada
                                                    platilloDescripcionTemp = ""
                                                    platilloEmojiTemp = when (categoriaSeleccionada) {
                                                        CategoriaPlatillo.COMIDA -> "🍔"
                                                        CategoriaPlatillo.ACOMPANAMIENTO -> "🍟"
                                                        CategoriaPlatillo.BEBIDA -> "🥤"
                                                        CategoriaPlatillo.POSTRE -> "🍩"
                                                        else -> "🍔"
                                                    }
                                                    showEditPlatilloDialog = true
                                                 },
                                                 colors = ButtonDefaults.buttonColors(
                                                     containerColor = Color(0xFF6750A4),
                                                     contentColor = Color.White
                                                 ),
                                                 modifier = Modifier.height(32.dp),
                                                 contentPadding = PaddingValues(horizontal = 10.dp),
                                                 shape = RoundedCornerShape(8.dp)
                                             ) {
                                                 Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                                 Spacer(modifier = Modifier.width(4.dp))
                                                 Text("Nuevo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                             }
                                         }
                                    }
                                }
                            }

                            // Horizontal Tab scroll for food categories
                            item {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(listOf(CategoriaPlatillo.COMIDA, CategoriaPlatillo.BEBIDA)) { cat ->
                                        val isCatSelected = categoriaSeleccionada == cat
                                        FilterChip(
                                            selected = isCatSelected,
                                            onClick = { categoriaSeleccionada = cat },
                                            label = { Text(cat.etiqueta, fontWeight = FontWeight.SemiBold) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        )
                                    }
                                }
                            }

                            // Grilla de platillos filtering corresponding category
                            item {
                                val filtrados = menuPlatillos.filter { it.categoria == categoriaSeleccionada }
                                if (filtrados.isEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                                        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("📭", fontSize = 48.sp)
                                            Text(
                                                text = "Categoría vacía o sin platillos",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1D1B20)
                                            )
                                            Text(
                                                text = "Crea un plato con el botón 'Nuevo' de arriba o restablece el menú estándar de inmediato.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF49454F),
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    menuPlatillos.clear()
                                                    menuPlatillos.addAll(MENU_ITEMS)
                                                    saveMenuToPrefs(sharedPrefs, menuPlatillos)
                                                    Toast.makeText(context, "Menú predefinido restaurado", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                                            ) {
                                                Text("Restablecer Menú Estándar")
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        filtrados.chunked(2).forEach { rowPlatillos ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                rowPlatillos.forEach { platillo ->
                                                    CardPlatillo(
                                                        platillo = platillo,
                                                        onAgregar = {
                                                            val index = carrito.indexOfFirst { it.platillo.nombre == platillo.nombre }
                                                            if (index != -1) {
                                                                carrito[index] = carrito[index].copy(cantidad = carrito[index].cantidad + 1)
                                                            } else {
                                                                carrito.add(ItemCart(platillo, 1))
                                                            }
                                                            Toast.makeText(context, "${platillo.nombre} +. Comanda: $mesaSeleccionada", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onEditar = {
                                                            editingPlatillo = platillo
                                                            platilloNombreTemp = platillo.nombre
                                                            platilloPrecioTemp = platillo.precio.toString()
                                                            platilloCategoriaTemp = platillo.categoria
                                                            platilloDescripcionTemp = platillo.descripcion
                                                            platilloEmojiTemp = platillo.emoji
                                                            showEditPlatilloDialog = true
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                if (rowPlatillos.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Comanda current box details embedded dynamically on Menu page
                            item {
                                ActiveComandaSummaryBox(
                                    mesaName = mesaSeleccionada,
                                    carrito = carrito,
                                    totalCarrito = totalCarrito,
                                    onEmpty = { },
                                    onNotesClick = { idx, item ->
                                        activeNotesCartIndex = idx
                                        notesTextTemp = item.notas
                                        showNotesDialog = true
                                    },
                                    onMinusClick = { idx, item ->
                                        if (item.cantidad > 1) {
                                            carrito[idx] = item.copy(cantidad = item.cantidad - 1)
                                        } else {
                                            carrito.removeAt(idx)
                                        }
                                    },
                                    onPlusClick = { idx, item ->
                                        carrito[idx] = item.copy(cantidad = item.cantidad + 1)
                                    },
                                    onEnviarClick = {
                                        isSending = true
                                        val itemsComanda = carrito.map { it.toItemPedido() }
                                        val comandaObj = Pedido(
                                            mesa = mesaSeleccionada,
                                            mesero = meseroNombre,
                                            items = itemsComanda,
                                            total = totalCarrito
                                        )
                                        repository.crearPedido(comandaObj) { exito, errorMsg ->
                                            isSending = false
                                            if (exito) {
                                                carrito.clear()
                                                activeTab = "pedidos" // Auto forward to kitchen tracker to monitor!
                                                Toast.makeText(context, "¡Comanda enviada a Cocina! 🍳🚀", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Error: $errorMsg ❌", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    isSending = isSending
                                )
                            }
                        }
                    }

                    // TAB: KITCHEN MONITOR (KDS feed displaying cook times live)
                    "pedidos" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "⏱️ Monitor en Cocina (KDS)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1D1B20)
                                        )
                                        Text(
                                            text = "Órdenes activas en cocina via WebSockets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF49454F)
                                        )
                                    }

                                    Button(
                                        onClick = { repository.refreshPedidos() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE8DEF8),
                                            contentColor = Color(0xFF1D192B)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Refrescar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Live Postgres Realtime WebSocket filter exclusively for this Mesero
                            val misOrdenesActivas = pedidosState.filter { 
                                it.mesero.equals(meseroNombre, ignoreCase = true) && 
                                it.estado != "pagado"
                            }

                            if (misOrdenesActivas.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp)
                                            .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "🛎️",
                                                fontSize = 44.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "Sin órdenes activas en cocina.",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF1D1B20),
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "No tienes comandas en preparación actualmente. Haz un nuevo pedido desde el Menú para verlo en tiempo real aquí.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF49454F),
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = { activeTab = "menu" },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.padding(top = 8.dp)
                                            ) {
                                                Text("Ver Menú de Platillos")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(misOrdenesActivas) { ped ->
                                    CardMonitorPedido(
                                        pedido = ped,
                                        onStatusUpdate = { nuevoEstado ->
                                            val id = ped.id
                                            if (id != null) {
                                                repository.actualizarEstadoPedido(id, nuevoEstado) { exito, errorMsg ->
                                                    if (exito) {
                                                        Toast.makeText(context, "Pedido #${ped.id} está ahora en: ${nuevoEstado.uppercase()}", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Error: $errorMsg ❌", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    // --- COOPERATIVE SPECIAL COOKING NOTES MODAL DIALOG ---
    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text("Añadir Notas Especiales 📝") },
            text = {
                Column {
                    Text(
                        "Ingresa aclaraciones o modificaciones al platillo (ej: 'Sin cebolla', 'Término medio', 'Salsa aparte'):",
                        fontSize = 13.sp,
                        color = Color(0xFF49454F)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notesTextTemp,
                        onValueChange = { notesTextTemp = it },
                        placeholder = { Text("ej: Término medio, sin cebolla...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("cooking_notes_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeNotesCartIndex != -1 && activeNotesCartIndex < carrito.size) {
                            carrito[activeNotesCartIndex] = carrito[activeNotesCartIndex].copy(notas = notesTextTemp)
                        }
                        showNotesDialog = false
                    }
                ) {
                    Text("De Acuerdo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // --- PREMIUM MESERO PROFILE & NETWORK CONNECTION CONFIG MODAL DIALOG ---
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Person, null, tint = Color(0xFF6750A4))
                    Text("Configuración de Perfil", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Avatar display representation
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6750A4))
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )
                    }

                    // Editable Name textfield
                    OutlinedTextField(
                        value = meseroNombre,
                        onValueChange = { meseroNombre = it },
                        label = { Text("Nombre del Mesero") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mesero_name_input_profile"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )

                    Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))

                    // Live Connection Details panel
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ESTADO DE CONEXIÓN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (connectionType == ConnectionType.NUBE) Color(0xFF137333) else Color(0xFFD97706))
                            )
                            Text(
                                text = if (connectionType == ConnectionType.NUBE) {
                                    if (isConnectingWS) "Sincronizando Nube (Conectando WS)" else "Sincronización Nube Activa"
                                } else {
                                    "Simulación / Modo Demo Local"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (connectionType == ConnectionType.NUBE) Color(0xFF137333) else Color(0xFFD97706)
                            )
                        }

                        Text(
                            text = if (connectionType == ConnectionType.NUBE) {
                                "Los datos se envían a PostgreSQL. Las actualizaciones críticas del KDS de cocina se asocian por red Websocket instantánea."
                            } else {
                                "Supabase URL/Key sin configurar en BuildConfig. Funciona en modo demo local sin conexión."
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF49454F)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        sharedPrefs.edit().putString("mesero_nombre", meseroNombre).apply()
                        showProfileDialog = false 
                    }
                ) {
                    Text("Confirmar")
                }
            }
        )
    }

    // --- DIÁLOGO DE BIENVENIDA (CONFIGURAR NOMBRE AL ENTRAR POR PRIMERA VEZ) ---
    if (showWelcomeDialog) {
        AlertDialog(
            onDismissRequest = { /* No descartable: el usuario debe ingresar un nombre para comenzar */ },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👋", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "¡Bienvenido a RestFlow!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Para comenzar a tomar pedidos o gestionar la cocina, por favor escribe tu nombre a continuación para registrar tus actividades.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = welcomeNombreTemp,
                        onValueChange = { welcomeNombreTemp = it },
                        label = { Text("Tu Nombre o Rol") },
                        placeholder = { Text("Ej: Mesero Carlos o Chef Sofía") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome_name_input_field"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { 
                            if (welcomeNombreTemp.isNotBlank()) {
                                meseroNombre = welcomeNombreTemp.trim()
                                sharedPrefs.edit().putString("mesero_nombre", meseroNombre).apply()
                                showWelcomeDialog = false
                                keyboardController?.hide()
                            }
                        })
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (welcomeNombreTemp.isNotBlank()) {
                            meseroNombre = welcomeNombreTemp.trim()
                            sharedPrefs.edit().putString("mesero_nombre", meseroNombre).apply()
                            showWelcomeDialog = false
                        }
                    },
                    enabled = welcomeNombreTemp.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Comenzar Ahora")
                }
            }
        )
    }

    // --- CONFIRMAR Y ENVIAR PEDIDO DIALOG ---
    if (showConfirmOrderDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmOrderDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, null, tint = Color(0xFF6750A4))
                    Text("Enviar Comanda - $mesaSeleccionada", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Revisa los productos agregados a la comanda antes de enviarla a cocina:")
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .background(Color(0xFFF7F2FA), RoundedCornerShape(8.dp))
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        carrito.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.platillo.emoji} ${item.cantidad}x ${item.platillo.nombre}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1D1B20),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$${String.format("%.2f", item.platillo.precio * item.cantidad)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6750A4)
                                )
                            }
                        }
                    }
                    
                    Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL CUENTA:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "$${String.format("%.2f", totalCarrito)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color(0xFF6750A4)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmOrderDialog = false
                        isSending = true
                        val itemsComanda = carrito.map { it.toItemPedido() }
                        val comandaObj = Pedido(
                            mesa = mesaSeleccionada,
                            mesero = meseroNombre,
                            items = itemsComanda,
                            total = totalCarrito
                        )
                        repository.crearPedido(comandaObj) { exito, errorMsg ->
                            isSending = false
                            if (exito) {
                                carrito.clear()
                                activeTab = "pedidos" // Cambiar a la pestaña de Cocina directamente para monitorearlo en vivo
                                Toast.makeText(context, "¡Comanda enviada a Cocina! 🍳🚀", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error: $errorMsg ❌", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enviar a Cocina", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmOrderDialog = false }) {
                    Text("Seguir Editando")
                }
            }
        )
    }

    // --- REVOLUTIONARY EDIT / CREATE PLATILLO DIALOG ---
    if (showEditPlatilloDialog) {
        AlertDialog(
            onDismissRequest = { showEditPlatilloDialog = false },
            title = {
                Text(
                    text = if (editingPlatillo == null) "Crear Nuevo Platillo" else "Editar Platillo",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    // Nombre input
                    OutlinedTextField(
                        value = platilloNombreTemp,
                        onValueChange = { platilloNombreTemp = it },
                        label = { Text("Nombre del Platillo") },
                        placeholder = { Text("Ej. Papas con Queso") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Precio & Emoji Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = platilloPrecioTemp,
                            onValueChange = { platilloPrecioTemp = it },
                            label = { Text("Precio ($)") },
                            placeholder = { Text("0.00") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = platilloEmojiTemp,
                            onValueChange = { platilloEmojiTemp = it },
                            label = { Text("Emoji") },
                            placeholder = { Text("🍔") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Categoría selector
                    Text(
                        text = "Categoría",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF49454F)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(CategoriaPlatillo.COMIDA, CategoriaPlatillo.BEBIDA).forEach { cat ->
                            val isSel = platilloCategoriaTemp == cat
                            FilterChip(
                                selected = isSel,
                                onClick = { platilloCategoriaTemp = cat },
                                label = { Text(cat.etiqueta, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Descripción input
                    OutlinedTextField(
                        value = platilloDescripcionTemp,
                        onValueChange = { platilloDescripcionTemp = it },
                        label = { Text("Fórmula / Notas de Receta") },
                        placeholder = { Text("Ej. Aderezo cheddar derretido, cebollín picado.") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedPrecio = platilloPrecioTemp.toDoubleOrNull()
                        if (platilloNombreTemp.isBlank()) {
                            Toast.makeText(context, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (parsedPrecio == null || parsedPrecio < 0.0) {
                            Toast.makeText(context, "Ingresa un precio válido", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val edited = MenuPlatillo(
                            nombre = platilloNombreTemp.trim(),
                            precio = parsedPrecio,
                            categoria = platilloCategoriaTemp,
                            descripcion = platilloDescripcionTemp.trim(),
                            emoji = if (platilloEmojiTemp.isBlank()) "🍔" else platilloEmojiTemp.trim()
                        )

                        val original = editingPlatillo
                        if (original == null) {
                            // Verify uniqueness
                            if (menuPlatillos.any { it.nombre.equals(edited.nombre, ignoreCase = true) }) {
                                Toast.makeText(context, "Ya existe un platillo con ese nombre", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            menuPlatillos.add(edited)
                            Toast.makeText(context, "¡Platillo creado con éxito! 🍕🌱", Toast.LENGTH_SHORT).show()
                        } else {
                            // Find and update item by name or reference
                            val targetIndex = menuPlatillos.indexOfFirst { it.nombre == original.nombre }
                            if (targetIndex != -1) {
                                menuPlatillos[targetIndex] = edited
                                // Also update matching items in the current comanda/cart so lines don't break
                                val cartIndex = carrito.indexOfFirst { it.platillo.nombre == original.nombre }
                                if (cartIndex != -1) {
                                    carrito[cartIndex] = carrito[cartIndex].copy(platillo = edited)
                                }
                                Toast.makeText(context, "¡Platillo modificado con éxito!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        saveMenuToPrefs(sharedPrefs, menuPlatillos)
                        showEditPlatilloDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (editingPlatillo == null) "Crear" else "Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val original = editingPlatillo
                    if (original != null) {
                        // Delete Button!
                        TextButton(
                            onClick = {
                                // Delete the item
                                menuPlatillos.removeIf { it.nombre == original.nombre }
                                saveMenuToPrefs(sharedPrefs, menuPlatillos)
                                // Also remove from current cart/comanda
                                carrito.removeIf { it.platillo.nombre == original.nombre }
                                showEditPlatilloDialog = false
                                Toast.makeText(context, "Platillo eliminado de la carta", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3261E))
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Eliminar")
                        }
                    }
                    TextButton(onClick = { showEditPlatilloDialog = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

// --------------------------------------------------------------------
// CARD PLATILLO COMPONENT STYLED WITH INTENTIONAL ROUNDED-2XL CORNERS
// --------------------------------------------------------------------
@Composable
fun CardPlatillo(
    platillo: MenuPlatillo,
    onAgregar: () -> Unit,
    onEditar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("platillo_card_${platillo.nombre}")
            .clickable { onAgregar() },
        shape = RoundedCornerShape(16.dp), // rounded-2xl matching
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)), // vibrant container tone
        border = BorderStroke(1.dp, Color(0xFFCAC4D0)) // custom outline styling
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = platillo.emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFE8DEF8)) // vibrant secondary background pill
                            .padding(6.dp)
                    )

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

                Text(
                    text = "$${String.format("%.2f", platillo.precio)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF6750A4) // Primary purple total indicator
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = platillo.nombre,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1D1B20),
                maxLines = 1
            )

            Text(
                text = platillo.descripcion,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF49454F),
                maxLines = 2,
                minLines = 2,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAgregar,
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

// --------------------------------------------------------------------
// CARD MONITOR PEDIDO KDS COMPONENT STYLED WITH DISTINCT RELEVANT COLOR BADGES
// --------------------------------------------------------------------
@Composable
fun CardMonitorPedido(
    pedido: Pedido,
    onStatusUpdate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Aesthetic states according to kitchen feedback
    val statusProps = when (pedido.estado) {
        "pendiente" -> Triple(Color(0xFFB3261E), Color(0xFFEFB8C8).copy(alpha = 0.2f), "📝 PENDIENTE")
        "cocinando" -> Triple(Color(0xFFF59E0B), Color(0xFFFFF7E6), "🔥 COCINANDO")
        "listo" -> Triple(Color(0xFF1A73E8), Color(0xFFE8DEF8), "🛎️ ¡LISTO PARA SERVIR!")
        "entregado" -> Triple(Color(0xFF6750A4), Color(0xFFE8DEF8).copy(alpha = 0.5f), "🍽️ SERVIDO")
        else -> Triple(Color.Gray, Color(0xFFF7F2FA), pedido.estado.uppercase())
    }

    val (colorEstatus, bgBadge, textEstatus) = statusProps

    val isReady = pedido.estado == "listo"

    val borderStroke = if (isReady) {
        BorderStroke(2.dp, Color(0xFF1A73E8)) // flashy focus border
    } else {
        BorderStroke(1.dp, Color(0xFFCAC4D0))
    }

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
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF49454F)
                    )
                }

                // Vibrant Badge
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

            // Dish break-down list
            val itemsString = pedido.items.joinToString(", ") { "${it.cantidad}x ${it.producto}" }
            Text(
                text = "Platillos: $itemsString",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1D1B20),
                fontWeight = FontWeight.Medium
            )

            // Alert banner if ready for serving
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
                        contentDescription = "Notificación listo", 
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

            // Action chip buttons to change state
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Empezar Cocina", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "cocinando" -> {
                            Button(
                                onClick = { onStatusUpdate("listo") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Listo para Servir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "listo" -> {
                            Button(
                                onClick = { onStatusUpdate("entregado") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Marcar Entregado", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "entregado" -> {
                            Button(
                                onClick = { onStatusUpdate("pagado") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF137333), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
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

// --------------------------------------------------------------------
// COOPERATIVE ACTIVE COMANDA DETAILS COMPOSABLE HELPER CARD
// --------------------------------------------------------------------
@Composable
fun ActiveComandaSummaryBox(
    mesaName: String,
    carrito: List<ItemCart>,
    totalCarrito: Double,
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
                        Text(
                            text = "$${String.format("%.2f", totalCarrito)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF6750A4)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Primary submit/checkout button
                    Button(
                        onClick = onEnviarClick,
                        modifier = Modifier
                            .fillMaxWidth()
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

// --------------------------------------------------------------------
// REAL-TIME KITCHEN DISPLAY SYSTEM (KDS) COMPONENTS
// --------------------------------------------------------------------
@Composable
fun CocinaScreenContent(
    pedidos: List<Pedido>,
    filtro: String,
    onFiltroChange: (String) -> Unit,
    onStatusUpdate: (Long, String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter active kitchen orders: omit completed and paid bills by default, unless filtered
    val kitchenOrders = remember(pedidos, filtro) {
        pedidos.filter { ped ->
            when (filtro) {
                "pendiente" -> ped.estado == "pendiente"
                "cocinando" -> ped.estado == "cocinando"
                "listo" -> ped.estado == "listo"
                else -> ped.estado != "pagado" && ped.estado != "entregado"
            }
        }.sortedByDescending { it.id ?: 0L }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141318)) // Dedicated high-tech dark charcoal KDS background
            .padding(16.dp)
    ) {
        // Kitchen Header Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB4AB))
                    )
                    Text(
                        text = "REAL-TIME KITCHEN FEED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = Color(0xFFFFB4AB)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "🧑‍🍳 Comandas de Cocina",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
            }

            // Sync Database Controls
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .background(Color(0xFF2D2A33), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sincronizar",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Kitchen Filters Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filtrosList = listOf(
                "todos" to "Todos 📋",
                "pendiente" to "Recibidos 🪵",
                "cocinando" to "Cocinando 🔥",
                "listo" to "Listos 🛎️"
            )

            filtrosList.forEach { (key, label) ->
                val isSelected = filtro == key
                Surface(
                    color = if (isSelected) Color(0xFFFFB4AB) else Color(0xFF211F26),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onFiltroChange(key) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) Color(0xFF410002) else Color(0xFFE6E1E5),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Kitchen Board Orders Grid
        if (kitchenOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🍽️", fontSize = 48.sp)
                    Text(
                        text = "No hay órdenes activas",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.LightGray
                    )
                    Text(
                        text = "Los nuevos pedidos que envíen los meseros se recibirán de forma inmediata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(kitchenOrders, key = { it.id ?: 0L }) { ped ->
                    CardCocinaPedido(
                        pedido = ped,
                        onStatusUpdate = onStatusUpdate
                    )
                }
            }
        }
    }
}

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

    // Dynamic elapsed time
    val ticketAgeText = remember(pedido.id) {
        val calculatedAge = if (pedido.id != null) {
            ((System.currentTimeMillis() - (pedido.id!! * 1000)) % 15).coerceAtLeast(1)
        } else {
            2
        }
        "Iniciado hace $calculatedAge Min"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Table, order ID, elapsed age
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFB4AB), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = pedido.mesa.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF410002)
                        )
                    }

                    Text(
                        text = "Ticket #${pedido.id ?: "Temp"}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.LightGray
                    )
                }

                Text(
                    text = "⏳ $ticketAgeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(thickness = 1.dp, color = Color(0xFF49454F).copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Requested dishes list
            Text(
                text = "DETALLE DE PREPARACIÓN:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = Color(0xFFFFB4AB)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pedido.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Circular Badge for Quantities
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFB4AB).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${item.cantidad}x",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFB4AB)
                                )
                            }

                            Column {
                                Text(
                                    text = item.producto,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                if (item.notas.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text("📝", fontSize = 11.sp)
                                        Text(
                                            text = item.notas,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFFFB4AB)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(thickness = 1.dp, color = Color(0xFF49454F).copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(14.dp))

            // Bottom actions and details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waiter context info
                Column {
                    Text(
                        text = "SOLICITADO POR:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = pedido.mesero,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // Action controls based on status
                val pedId = pedido.id
                if (pedId != null) {
                    when (pedido.estado) {
                        "pendiente" -> {
                            Button(
                                onClick = { onStatusUpdate(pedId, "cocinando") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB4AB),
                                    contentColor = Color(0xFF410002)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Preparar",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Empezar Cocina 🔥", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        "cocinando" -> {
                            Button(
                                onClick = { onStatusUpdate(pedId, "listo") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF81C784),
                                    contentColor = Color(0xFF1B5E20)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Listo para Servir",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Listo para Servir 🛎️", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        "listo" -> {
                            Surface(
                                color = Color(0xFF1B5E20),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Terminado",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Listo / Avisado",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
