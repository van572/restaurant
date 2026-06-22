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
import com.example.ui.components.*
import com.example.data.*
import com.example.ui.PrintUtils
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeseroScreen(
    repository: PedidoRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe StateFlows del Repositorio
    val pedidosState by repository.pedidos.collectAsState()
    val inventarioState by repository.inventario.collectAsState()
    val connectionType by repository.connectionState.collectAsState()
    val isConnectingWS by repository.isConnectingWS.collectAsState()

    // Estados Locales de UI y Persistencia básica de Sesión del Mesero (Entrando por primera vez)
    val sharedPrefs = remember { context.getSharedPreferences("rest_flow_prefs", android.content.Context.MODE_PRIVATE) }
    var meseroNombre by remember { 
        mutableStateOf(sharedPrefs.getString("mesero_nombre", "") ?: "") 
    }
    var isLoggedIn by remember { mutableStateOf(meseroNombre.isNotBlank()) }
    var tasaCambio by remember { mutableFloatStateOf(sharedPrefs.getFloat("tasa_cambio", 45.5f)) }
    var userEmail by remember { mutableStateOf("") }
    var userPass by remember { mutableStateOf("") }
    var isAuthLoading by remember { mutableStateOf(false) }

    var showWelcomeDialog by remember { mutableStateOf(false) }
    var welcomeNombreTemp by remember { mutableStateOf("") }
    var mesaSeleccionada by remember { mutableStateOf("Mesa 1") }
    var categoriaSeleccionada by remember { mutableStateOf("ALMUERZO") }
    var menuSearchQuery by remember { mutableStateOf("") }
    
    val customCategories = remember {
        mutableStateListOf<String>().apply {
            addAll(loadCategoriesFromPrefs(sharedPrefs))
        }
    }
    
    // --- CHEF / KITCHEN STATE CONFIGS ---
    var userRole by remember { mutableStateOf("mesero") } // "mesero" o "cocinero"
    var lastKnownMaxId by remember { mutableStateOf<Long?>(null) }
    var activeNewOrderNotification by remember { mutableStateOf<Pedido?>(null) }
    var cocineroFiltroEstado by remember { mutableStateOf("todos") } // "todos", "pendiente", "cocinando", "listo"
    var activeOrderReadyNotification by remember { mutableStateOf<Pedido?>(null) }
    
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
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInputTemp by remember { mutableStateOf("") }
    var platilloParaPeso by remember { mutableStateOf<MenuPlatillo?>(null) }

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
    var platilloCategoriaTemp by remember { mutableStateOf("ALMUERZO") }
    var platilloDescripcionTemp by remember { mutableStateOf("") }
    var platilloEmojiTemp by remember { mutableStateOf("🍔") }
    var platilloInventarioIdTemp by remember { mutableStateOf<Long?>(null) }
    var platilloEsPesoTemp by remember { mutableStateOf(false) }

    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryNameTemp by remember { mutableStateOf("") }

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
                scope.launch {
                    snackbarHostState.showSnackbar("🔔 Alertas e hilos de cocina activados")
                }
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

    // Monitoreo en tiempo real de pedidos listos para el mesero
    val notifiedReadyIds = remember { mutableStateListOf<Long>() }
    LaunchedEffect(pedidosState) {
        if (userRole == "mesero" && meseroNombre.isNotBlank()) {
            val newlyReady = pedidosState.find { ped ->
                ped.estado == "listo" && 
                ped.mesero.equals(meseroNombre, ignoreCase = true) && 
                !notifiedReadyIds.contains(ped.id ?: 0L)
            }
            
            if (newlyReady != null) {
                activeOrderReadyNotification = newlyReady
                notifiedReadyIds.add(newlyReady.id ?: 0L)
                
                // Play notification sound
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                } catch (e: Exception) { e.printStackTrace() }
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

    if (!isLoggedIn) {
        StaffLoginScreen(
            onLoginSuccess = { email, role ->
                userEmail = email
                userRole = role
                isLoggedIn = true
                meseroNombre = email.split("@")[0].capitalize()
                sharedPrefs.edit().putString("mesero_nombre", meseroNombre).apply()
            },
            isAuthLoading = isAuthLoading,
            repository = repository
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        // Cerrar Sesión (Return to Role Selector)
                        IconButton(
                            onClick = { 
                                meseroNombre = ""
                                sharedPrefs.edit().putString("mesero_nombre", "").apply()
                                // No formal logout needed since we switched to local role selection
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF7F2FA))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Cerrar Sesión",
                                tint = Color(0xFFB3261E),
                                modifier = Modifier.size(20.dp)
                            )
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
                                scope.launch {
                                    snackbarHostState.showSnackbar("Sincronizando...")
                                }
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
            if (userRole == "mesero") {
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

                        // TAB 3: Historial (Past orders)
                        val isHistorialActive = activeTab == "historial"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = "historial" }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isHistorialActive) Color(0xFFE8DEF8) else Color.Transparent)
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Historial",
                                    tint = if (isHistorialActive) Color(0xFF1D192B) else Color(0xFF49454F),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Historial",
                                fontSize = 11.sp,
                                fontWeight = if (isHistorialActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHistorialActive) Color(0xFF1D192B) else Color(0xFF49454F)
                            )
                        }

                        // TAB 5: Perfil config trigger
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

            // Banner para PEDIDO LISTO (Waiters only)
            AnimatedVisibility(
                visible = activeOrderReadyNotification != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                activeOrderReadyNotification?.let { ped ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A73E8)) // Modern Blue for ready status
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clickable {
                                activeOrderReadyNotification = null
                                activeTab = "pedidos" // Show the orders list to find it
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🍳", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "ORDEN LISTA: ${ped.mesa}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "La cocina ha terminado la preparación. ¡Corre a servirlo!",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        IconButton(onClick = { activeOrderReadyNotification = null }) {
                            Icon(Icons.Default.Check, contentDescription = "Entendido", tint = Color.White)
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
                                scope.launch {
                                    snackbarHostState.showSnackbar("Pedido actualizado a: ${nuevoEstado.uppercase()}")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Error: $error ❌")
                                }
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
                    "pedidos" to "Cocina",
                    "historial" to "Historial"
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
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .padding(bottom = 8.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_restaurant_banner_1781876475366),
                                            contentDescription = "Restaurant Banner",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                                    )
                                                )
                                        )
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(16.dp)
                                        ) {
                                            Text(
                                                text = "¡Buen día, $meseroNombre!",
                                                color = Color.White,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Gestión de mesas y comandas activas",
                                                color = Color.White.copy(alpha = 0.8f),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

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
                                                            scope.launch {
                                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                                snackbarHostState.showSnackbar("$mesa seleccionada para ordenar")
                                                            }
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
                                    tasaCambio = tasaCambio,
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
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("¡Comanda enviada a Cocina! 🍳🚀")
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Error: $errorMsg ❌")
                                                }
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
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Menú restablecido a predefinidos")
                                                    }
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
                                                    platilloCategoriaTemp = if (categoriaSeleccionada == "Todos") customCategories.firstOrNull() ?: "ALMUERZO" else categoriaSeleccionada
                                                    platilloDescripcionTemp = ""
                                                    platilloEmojiTemp = when (platilloCategoriaTemp) {
                                                        "DESAYUNO" -> "🍳"
                                                        "ALMUERZO" -> "🥘"
                                                        "CENA" -> "🌙"
                                                        "BEBIDA" -> "🥤"
                                                        "POSTRE" -> "🍰"
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = menuSearchQuery,
                                        onValueChange = { 
                                            menuSearchQuery = it 
                                            // Lógica de escaneo de código de barras (7 a 13 dígitos numéricos)
                                            if (it.length >= 7 && it.all { c -> c.isDigit() }) {
                                                scope.launch {
                                                    val foundInv = repository.getInventarioByBarcode(it)
                                                    if (foundInv != null) {
                                                        // Intentar matchear con un platillo que use este inventario
                                                        val linkedPlat = menuPlatillos.find { p -> p.inventarioDependienteId == foundInv.id }
                                                        if (linkedPlat != null) {
                                                            val idx = carrito.indexOfFirst { it.platillo.nombre == linkedPlat.nombre }
                                                            if (idx != -1) {
                                                                carrito[idx] = carrito[idx].copy(cantidad = carrito[idx].cantidad + 1)
                                                            } else {
                                                                carrito.add(ItemCart(linkedPlat, 1))
                                                            }
                                                            menuSearchQuery = "" // Limpiar para el siguiente escaneo
                                                            snackbarHostState.showSnackbar("Escaneado: ${foundInv.nombre} added.")
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        placeholder = { Text("Buscar platillo...", fontSize = 14.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                                        trailingIcon = {
                                            if (menuSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { menuSearchQuery = "" }) {
                                                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .testTag("menu_search_input"),
                                        shape = RoundedCornerShape(16.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = Color(0xFFF7F2FA),
                                            focusedContainerColor = Color.White,
                                            unfocusedBorderColor = Color(0xFFCAC4D0).copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }

                            item {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item {
                                        FilterChip(
                                            selected = categoriaSeleccionada == "Todos",
                                            onClick = { categoriaSeleccionada = "Todos" },
                                            label = { Text("Todos") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF6750A4),
                                                selectedLabelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    items(customCategories) { cat ->
                                        val isCatSelected = categoriaSeleccionada == cat
                                        FilterChip(
                                            selected = isCatSelected,
                                            onClick = { categoriaSeleccionada = cat },
                                            label = { Text(cat, fontWeight = FontWeight.SemiBold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFFE8DEF8),
                                                selectedLabelColor = Color(0xFF1D192B)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        )
                                    }
                                    item {
                                        IconButton(
                                            onClick = { showNewCategoryDialog = true },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF6750A4).copy(alpha = 0.1f))
                                        ) {
                                            Icon(Icons.Default.Add, null, tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Grilla de platillos filtering corresponding category and search query
                            item {
                                val filtrados = menuPlatillos.filter { 
                                    (categoriaSeleccionada == "Todos" || it.categoria == categoriaSeleccionada) && 
                                    (menuSearchQuery.isEmpty() || it.nombre.contains(menuSearchQuery, ignoreCase = true) || it.descripcion.contains(menuSearchQuery, ignoreCase = true))
                                }
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
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Menú predefinido restaurado")
                                                    }
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
                                                rowPlatillos.forEach { pItem ->
                                                    CardPlatillo(
                                                        platillo = pItem,
                                                        inventario = inventarioState,
                                                        onAgregar = {
                                                            if (pItem.esPorPeso) {
                                                                platilloParaPeso = pItem
                                                                weightInputTemp = ""
                                                                showWeightDialog = true
                                                            } else {
                                                                val idx = carrito.indexOfFirst { it.platillo.nombre == pItem.nombre }
                                                                if (idx != -1) {
                                                                    carrito[idx] = carrito[idx].copy(cantidad = carrito[idx].cantidad + 1)
                                                                } else {
                                                                    carrito.add(ItemCart(pItem, 1))
                                                                }
                                                                scope.launch {
                                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                                    snackbarHostState.showSnackbar("${pItem.nombre} +. Comanda: $mesaSeleccionada", duration = SnackbarDuration.Short)
                                                                }
                                                            }
                                                        },
                                                        tasaCambio = tasaCambio,
                                                        onEditar = {
                                                            editingPlatillo = pItem
                                                            platilloNombreTemp = pItem.nombre
                                                            platilloPrecioTemp = pItem.precio.toString()
                                                            platilloCategoriaTemp = pItem.categoria
                                                            platilloDescripcionTemp = pItem.descripcion
                                                            platilloEmojiTemp = pItem.emoji
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
                                    tasaCambio = tasaCambio,
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
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("¡Comanda enviada a Cocina! 🍳🚀")
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Error: $errorMsg ❌")
                                                }
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
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Pedido #${ped.id} está ahora en: ${nuevoEstado.uppercase()}")
                                                        }
                                                    } else {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Error: $errorMsg ❌")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "historial" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                        ) {
                            item {
                                Column {
                                    Text(
                                        text = "📜 Historial de Órdenes",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1D1B20)
                                    )
                                    Text(
                                        text = "Pedidos ya servidos o pagados",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF49454F)
                                    )
                                }
                            }
                            
                            val historial = pedidosState.filter { 
                                (it.mesero.equals(meseroNombre, ignoreCase = true) || meseroNombre.isBlank()) && 
                                (it.estado == "entregado" || it.estado == "pagado")
                            }.sortedByDescending { it.id }

                            if (historial.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📭", fontSize = 48.sp)
                                            Text(
                                                "Sin historial reciente",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF49454F)
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(historial) { ped ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = ped.mesa,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 18.sp
                                                )
                                                Surface(
                                                    color = if (ped.estado == "pagado") Color(0xFFE6F4EA) else Color(0xFFF1F3F4),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = ped.estado.uppercase(),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (ped.estado == "pagado") Color(0xFF137333) else Color(0xFF3C4043)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ped.items.forEach { itm ->
                                                Text(
                                                    text = "${itm.cantidad}x ${itm.producto}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFF49454F)
                                                )
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Total:",
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "$${String.format("%.2f", ped.total)}",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF6750A4)
                                                    )
                                                    Text(
                                                        text = "VES ${String.format("%.2f", ped.total * tasaCambio)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF137333),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    val html = PrintUtils.generateReceiptHtml(ped.mesa, ped.items, ped.total, tasaCambio.toDouble())
                                                    PrintUtils.printTicket(context, html)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Re-imprimir Ticket", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    // --- WEIGHT SELECTION DIALOG (Venta por Peso) ---
    if (showWeightDialog && platilloParaPeso != null) {
        val prod = platilloParaPeso!!
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("⚖️ Venta por Peso: ${prod.nombre}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ingresa el peso o cantidad decimal (Kg/Un):", fontSize = 13.sp)
                    OutlinedTextField(
                        value = weightInputTemp,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) weightInputTemp = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.500") },
                        suffix = { 
                            val invItem = inventarioState.firstOrNull { it.id == prod.inventarioDependienteId }
                            Text(invItem?.unidadMedida ?: "Kg") 
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val weight = weightInputTemp.toDoubleOrNull() ?: 0.0
                        if (weight > 0) {
                            val invItem = inventarioState.firstOrNull { it.id == prod.inventarioDependienteId }
                            val weightedPlatillo = prod.copy(precio = prod.precio * weight, nombre = "${prod.nombre} (${weight} ${invItem?.unidadMedida ?: "Kg"})")
                            carrito.add(ItemCart(weightedPlatillo, 1))
                            showWeightDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF137333))
                ) {
                    Text("AGREGAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) { Text("CANCELAR") }
            }
        )
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
                    
                    val quickNotes = listOf("Sin cebolla", "Extra queso", "Para llevar", "Salsa aparte", "Término medio")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickNotes) { note ->
                            SuggestionChip(
                                onClick = { 
                                    notesTextTemp = if (notesTextTemp.isBlank()) note else "$notesTextTemp, $note"
                                },
                                label = { Text(note, fontSize = 11.sp) }
                            )
                        }
                    }
                    
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

                    // Tasa del día simple display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8))
                    ) {
                        Text(
                            "Tasa del día: ${String.format("%.2f", tasaCambio)} VES/USD",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Logout Button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.logout()
                                isLoggedIn = false
                                showProfileDialog = false
                                sharedPrefs.edit().remove("mesero_nombre").apply()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cerrar Sesión")
                    }

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
                                scope.launch {
                                    snackbarHostState.showSnackbar("¡Comanda enviada a Cocina! 🍳🚀")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Error: $errorMsg ❌")
                                }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (editingPlatillo == null) Icons.Default.Add else Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF6750A4)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (editingPlatillo == null) "Crear Nuevo Platillo" else "Editar Platillo",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    // SECCIÓN 1: Identidad Visual
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEADDFF))
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                             Text("IDENTIDAD DEL PLATO", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                             
                             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                 Box(
                                     modifier = Modifier
                                         .size(64.dp)
                                         .clip(RoundedCornerShape(12.dp))
                                         .background(Color.White)
                                         .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp)),
                                     contentAlignment = Alignment.Center
                                 ) {
                                     Text(platilloEmojiTemp, fontSize = 32.sp)
                                 }
                                 
                                 OutlinedTextField(
                                     value = platilloNombreTemp,
                                     onValueChange = { platilloNombreTemp = it },
                                     label = { Text("Nombre del Plato") },
                                     placeholder = { Text("Ej. Ceviche Especial") },
                                     singleLine = true,
                                     modifier = Modifier.weight(1f),
                                     shape = RoundedCornerShape(12.dp)
                                 )
                             }
                             
                             Text("Símbolo sugerido:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF49454F))
                             LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                 val suggestedEmojis = listOf("🍔", "🍕", "🌮", "🥗", "🥪", "🍰", "🍵", "🍳", "🍗", "🍟", "🥣", "🍦", "🍹", "🥩", "🍷", "🍺", "☕")
                                 items(suggestedEmojis) { emoji ->
                                     Box(
                                         modifier = Modifier
                                             .size(40.dp)
                                             .clip(CircleShape)
                                             .background(if (platilloEmojiTemp == emoji) Color(0xFFE8DEF8) else Color.White)
                                             .clickable { platilloEmojiTemp = emoji }
                                             .border(1.dp, if (platilloEmojiTemp == emoji) Color(0xFF6750A4) else Color(0xFFCAC4D0), CircleShape),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Text(emoji, fontSize = 20.sp)
                                     }
                                 }
                             }
                        }
                    }

                    // SECCIÓN 2: Comercial
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEADDFF))
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                             Text("PRECIO Y CATEGORÍA", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                             
                             OutlinedTextField(
                                 value = platilloPrecioTemp,
                                 onValueChange = { platilloPrecioTemp = it },
                                 label = { Text("Precio de Venta") },
                                 prefix = { Text("$ ", color = Color(0xFF137333), fontWeight = FontWeight.Bold) },
                                 keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                 modifier = Modifier.fillMaxWidth(),
                                 shape = RoundedCornerShape(12.dp),
                                 singleLine = true
                             )

                             Text("Categoría del Menú", style = MaterialTheme.typography.labelSmall, color = Color(0xFF49454F))
                             FlowRow(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                             ) {
                                 customCategories.forEach { cat ->
                                     val isSel = platilloCategoriaTemp == cat
                                     FilterChip(
                                         selected = isSel,
                                         onClick = { platilloCategoriaTemp = cat },
                                         label = { Text(cat, fontSize = 11.sp) },
                                         leadingIcon = if (isSel) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) } } else null,
                                         shape = RoundedCornerShape(8.dp),
                                         colors = FilterChipDefaults.filterChipColors(
                                             selectedContainerColor = Color(0xFF6750A4),
                                             selectedLabelColor = Color.White,
                                             selectedLeadingIconColor = Color.White
                                         )
                                     )
                                 }
                             }
                        }
                    }

                    // SECCIÓN 3: Configuración
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("DETALLES OPERATIVOS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF49454F), fontWeight = FontWeight.Bold)
                            
                            OutlinedTextField(
                                value = platilloDescripcionTemp,
                                onValueChange = { platilloDescripcionTemp = it },
                                label = { Text("Descripción (Opcional)") },
                                placeholder = { Text("Ej. Pan artesanal, 150g de carne...") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            // Inventory link
                            var expandedInv by remember { mutableStateOf(false) }
                            val selectedInv = inventarioState.firstOrNull { it.id == platilloInventarioIdTemp }
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Vínculo con Inventario", style = MaterialTheme.typography.labelSmall, color = Color(0xFF49454F))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        onClick = { expandedInv = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (selectedInv != null) Color(0xFF137333) else Color(0xFFCAC4D0)),
                                        color = if (selectedInv != null) Color(0xFFE6F4EA) else Color.White
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (selectedInv != null) Icons.Default.Inventory else Icons.Default.Link,
                                                contentDescription = null,
                                                tint = if (selectedInv != null) Color(0xFF137333) else Color(0xFF49454F),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = selectedInv?.let { "${it.nombre} (${it.stock})" } ?: "Vincular Insumo...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (selectedInv != null) Color(0xFF137333) else Color(0xFF49454F),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF49454F))
                                        }
                                    }
                                    
                                    DropdownMenu(
                                        expanded = expandedInv,
                                        onDismissRequest = { expandedInv = false },
                                        modifier = Modifier.fillMaxWidth(0.7f)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("(Ninguno)", color = Color.Gray) },
                                            onClick = { platilloInventarioIdTemp = null; expandedInv = false }
                                        )
                                        inventarioState.forEach { inv ->
                                            DropdownMenuItem(
                                                text = { Text("${inv.nombre} [${inv.stock} ${inv.unidadMedida}]") },
                                                onClick = { platilloInventarioIdTemp = inv.id; expandedInv = false }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (platilloEsPesoTemp) Color(0xFFF3EDF7) else Color.Transparent)
                                    .clickable { platilloEsPesoTemp = !platilloEsPesoTemp }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = platilloEsPesoTemp,
                                    onCheckedChange = { platilloEsPesoTemp = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4))
                                )
                                Text(
                                    "Se vende por PESO (Kg / Gr)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (platilloEsPesoTemp) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedPrecio = platilloPrecioTemp.toDoubleOrNull()
                        if (platilloNombreTemp.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("Nombre requerido") }
                            return@Button
                        }
                        if (parsedPrecio == null || parsedPrecio < 0.0) {
                            scope.launch { snackbarHostState.showSnackbar("Precio inválido") }
                            return@Button
                        }

                        val edited = MenuPlatillo(
                            nombre = platilloNombreTemp.trim(),
                            precio = parsedPrecio,
                            categoria = platilloCategoriaTemp,
                            descripcion = platilloDescripcionTemp.trim(),
                            emoji = if (platilloEmojiTemp.isBlank()) "🍔" else platilloEmojiTemp.trim(),
                            esPorPeso = platilloEsPesoTemp,
                            inventarioDependienteId = platilloInventarioIdTemp
                        )

                        val original = editingPlatillo
                        if (original == null) {
                            if (menuPlatillos.any { it.nombre.equals(edited.nombre, ignoreCase = true) }) {
                                scope.launch { snackbarHostState.showSnackbar("El plato ya existe") }
                                return@Button
                            }
                            menuPlatillos.add(edited)
                            scope.launch { snackbarHostState.showSnackbar("¡Plato guardado!") }
                        } else {
                            val targetIndex = menuPlatillos.indexOfFirst { it.nombre == original.nombre }
                            if (targetIndex != -1) {
                                menuPlatillos[targetIndex] = edited
                                val cartIndex = carrito.indexOfFirst { it.platillo.nombre == original.nombre }
                                if (cartIndex != -1) carrito[cartIndex] = carrito[cartIndex].copy(platillo = edited)
                                scope.launch { snackbarHostState.showSnackbar("¡Plato actualizado!") }
                            }
                        }
                        saveMenuToPrefs(sharedPrefs, menuPlatillos)
                        showEditPlatilloDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (editingPlatillo == null) "GUARDAR PLATILLO" else "ACTUALIZAR DATOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val original = editingPlatillo
                    if (original != null) {
                        TextButton(
                            onClick = {
                                menuPlatillos.removeIf { it.nombre == original.nombre }
                                saveMenuToPrefs(sharedPrefs, menuPlatillos)
                                carrito.removeIf { it.platillo.nombre == original.nombre }
                                showEditPlatilloDialog = false
                                scope.launch { snackbarHostState.showSnackbar("Plato eliminado") }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3261E))
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Eliminar")
                        }
                    }
                    TextButton(onClick = { showEditPlatilloDialog = false }) {
                        Text("Cancelar", color = Color(0xFF49454F))
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    // --- NEW CATEGORY DIALOG ---
    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Category, null, tint = Color(0xFF6750A4))
                    Spacer(Modifier.width(12.dp))
                    Text("Nueva Categoría", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Clasifica tus productos para encontrarlos más rápido en la carta.", fontSize = 13.sp, color = Color(0xFF49454F))
                    OutlinedTextField(
                        value = newCategoryNameTemp,
                        onValueChange = { newCategoryNameTemp = it.uppercase() },
                        label = { Text("Nombre de la Categoría") },
                        placeholder = { Text("Ej: PARRILLAS, COCTELES") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryNameTemp.isNotBlank() && !customCategories.contains(newCategoryNameTemp)) {
                            customCategories.add(newCategoryNameTemp.trim())
                            saveCategoriesToPrefs(sharedPrefs, customCategories)
                            newCategoryNameTemp = ""
                            showNewCategoryDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Categoría creada con éxito")
                            }
                        }
                    },
                    enabled = newCategoryNameTemp.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                ) {
                    Text("CREAR CATEGORÍA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) { Text("Cancelar", color = Color(0xFF49454F)) }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }
}

