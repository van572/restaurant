package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.ui.CategoriaPlatillo
import com.example.ui.MenuPlatillo
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.PostgresAction.Update
import io.github.jan.supabase.realtime.PostgresAction.Insert
import io.github.jan.supabase.realtime.PostgresAction.Delete
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.io.IOException

// --------------------------------------------------------------------
// MODELO DE DATOS EN ESTRICTA RELACIÓN CON EL ESQUEMA POSTGRES
// --------------------------------------------------------------------
@Serializable
data class ItemPedido(
    val producto: String,
    val cantidad: Int,
    val precio: Double,
    val notas: String = ""
)

@Serializable
data class Pedido(
    val id: Long? = null,
    val mesa: String,
    val mesero: String,
    val items: List<ItemPedido>,
    val total: Double,
    val estado: String = "pendiente", // 'pendiente', 'cocinando', 'listo', 'entregado', 'pagado'
    val creado_en: String? = null,
    val actualizado_en: String? = null
)

@Serializable
data class PedidoInsert(
    val mesa: String,
    val mesero: String,
    val items: List<ItemPedido>,
    val total: Double,
    val estado: String = "pendiente"
)

// Representación de estado de conexión del cliente móvil
enum class ConnectionType {
    NUBE,
    MOCK_DEMO
}

class PedidoRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val TAG = "PedidoRepository"

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.pedidoDao()
    private val converters = PedidoRoomConverters()

    // Configuración obtenida via BuildConfig (.env / AI Studio Secrets)
    private val supabaseUrl = BuildConfig.SUPABASE_URL.trim()
    private val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotEmpty() && 
                supabaseUrl != "YOUR_SUPABASE_URL" && 
                supabaseAnonKey.isNotEmpty() && 
                supabaseAnonKey != "YOUR_SUPABASE_ANON_KEY"

    // Inicialización del SDK oficial de Supabase
    private val supabase: SupabaseClient? = if (isSupabaseConfigured) {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey
        ) {
            install(Postgrest)
            install(Realtime)
        }
    } else null

    // Estado reactivo expuesto a la UI
    private val _pedidos = MutableStateFlow<List<Pedido>>(emptyList())
    val pedidos: StateFlow<List<Pedido>> = _pedidos.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionType.MOCK_DEMO)
    val connectionState: StateFlow<ConnectionType> = _connectionState.asStateFlow()

    private val _isConnectingWS = MutableStateFlow(false)
    val isConnectingWS: StateFlow<Boolean> = _isConnectingWS.asStateFlow()

    private var realtimeJob: Job? = null
    private var isWebSocketConnected = false

    init {
        // Cargar pedidos guardados en Room para garantizar visualización offline inmediata
        scope.launch {
            cargarPedidosDesdeRoom()
        }

        if (supabase == null) {
            Log.i(TAG, "Arrancando en modo DEMO local (Supabase no configurado).")
            _connectionState.value = ConnectionType.MOCK_DEMO
            scope.launch {
                val existing = dao.getAllPedidos()
                if (existing.isEmpty()) {
                    inicializarMockDataEnRoom()
                } else {
                    cargarPedidosDesdeRoom()
                }
            }
        } else {
            Log.i(TAG, "Supabase detectado. Inicializando conexión Realtime SDK.")
            _connectionState.value = ConnectionType.NUBE
            inicializarSuscripcionRealtime()
            refreshPedidos()
        }
    }

    // --- CARGAR PEDIDOS DESDE ROOM HACIA STATEFLOW ---
    private suspend fun cargarPedidosDesdeRoom() {
        val entities = dao.getAllPedidos()
        val mapped = entities.map { it.toPedido(converters) }
        _pedidos.value = mapped.filter { it.estado != "pagado" }
    }

    // --- CONEXIÓN REALTIME CON EL SDK OFICIAL ---
    private fun inicializarSuscripcionRealtime() {
        val client = supabase ?: return
        realtimeJob?.cancel()
        
        realtimeJob = scope.launch {
            try {
                _isConnectingWS.value = true
                Log.i(TAG, "Conectando al canal Realtime de Supabase...")
                
                val channel = client.realtime.channel("pedidos_channel")
                
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public")
                
                _isConnectingWS.value = false
                isWebSocketConnected = true
                Log.i(TAG, "Suscripción Realtime activa para la tabla 'pedidos'.")

                channel.subscribe()

                changeFlow.onEach { action ->
                    Log.d(TAG, "Cambio Realtime detectado: $action")
                    
                    when (action) {
                        is Update -> {
                            val updatedPedido = action.decodeRecord<Pedido>()
                            Log.i(TAG, "Pedido #${updatedPedido.id} actualizado a '${updatedPedido.estado}'")
                            
                            // Notificación instantánea si el estado cambia a 'listo'
                            if (updatedPedido.estado == "listo") {
                                enviarNotificacionPedidoListo(updatedPedido)
                            }
                            refreshPedidos()
                        }
                        is Insert -> {
                            Log.i(TAG, "Nuevo pedido insertado en base de datos.")
                            refreshPedidos()
                        }
                        is Delete -> {
                            Log.i(TAG, "Pedido eliminado de la base de datos.")
                            refreshPedidos()
                        }
                        else -> refreshPedidos()
                    }
                }.collect()
                
            } catch (e: Exception) {
                isWebSocketConnected = false
                _isConnectingWS.value = false
                Log.e(TAG, "Error en la suscripción Realtime: ${e.message}")
                delay(5000)
                inicializarSuscripcionRealtime()
            }
        }
    }

    // --- ACCIÓN: ACTUALIZAR PEDIDOS DESDE LA WEB ---
    fun refreshPedidos() {
        val client = supabase
        if (client == null) {
            scope.launch {
                cargarPedidosDesdeRoom()
            }
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "Refrescando pedidos desde Supabase via SDK...")
                // Filtrar para no traer pedidos ya pagados
                val result = client.postgrest.from("pedidos")
                    .select {
                        filter {
                            neq("estado", "pagado")
                        }
                    }
                    .decodeList<Pedido>()
                
                // Sincronizar en caliente hacia Room local para disponibilidad offline inmediata
                val entities = result.map { PedidoEntity.fromPedido(it, it.id ?: System.currentTimeMillis(), converters) }
                dao.insertPedidos(entities)
                
                // Leer de la base de datos local unificada
                cargarPedidosDesdeRoom()
                Log.d(TAG, "Pedidos actualizados exitosamente en Room desde Supabase Nube. Total: ${result.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error refrescando pedidos en la nube, usando fallback offline de Room", e)
                cargarPedidosDesdeRoom()
            }
        }
    }

    // --- CARGAR EL MENÚ DYNAMIC DE SUPABASE ---
    fun fetchDynamicMenu(onResult: (List<MenuPlatillo>?) -> Unit) {
        val client = supabase
        if (client == null) {
            onResult(null)
            return
        }
        scope.launch {
            try {
                val result = client.postgrest.from("menu").select().decodeList<MenuPlatillo>()
                withContext(Dispatchers.Main) {
                    onResult(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recuperando el menú dinámico", e)
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    // --- NOTIFICACIÓN AL MESERO ---
    private fun enviarNotificacionPedidoListo(pedido: Pedido) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "pedidos_listos_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Pedidos Listos",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para platos que ya salieron de cocina"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("¡Pedido Listo! 🍽️")
            .setContentText("${pedido.mesa} ya tiene sus platos listos.")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("Mesa: ${pedido.mesa}\nMensaje: La cocina ha marcado el pedido como listo para servir.\nMesero asignado: ${pedido.mesero}"))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        // Realizar una vibración manual adicional si es posible
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo hacer vibrar el dispositivo: ${e.message}")
        }

        notificationManager.notify(pedido.id?.toInt() ?: System.currentTimeMillis().toInt(), builder.build())
        Log.i(TAG, "Notificación intrusiva enviada para pedido ${pedido.id}")
    }

    // --- ACCIÓN: CREAR UN NUEVO PEDIDO ---
    fun crearPedido(pedido: Pedido, onResult: (Boolean, String?) -> Unit) {
        val client = supabase
        if (client == null) {
            scope.launch {
                val nuevoId = System.currentTimeMillis()
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                val nuevoPedido = pedido.copy(id = nuevoId, creado_en = timestamp, actualizado_en = timestamp)
                
                // Guardar localmente en Room
                dao.insertPedido(PedidoEntity.fromPedido(nuevoPedido, nuevoId, converters))
                cargarPedidosDesdeRoom()
                
                withContext(Dispatchers.Main) {
                    onResult(true, "Pedido simulado guardado de manera local (Mesa: ${pedido.mesa})")
                }

                // Generar ciclo de simulación offline para pruebas en la cocina
                generarSimulacionCocinaOffline(nuevoId)
            }
            return
        }

        scope.launch {
            try {
                val insertModel = PedidoInsert(
                    mesa = pedido.mesa,
                    mesero = pedido.mesero,
                    items = pedido.items,
                    total = pedido.total,
                    estado = pedido.estado
                )
                
                client.postgrest.from("pedidos").insert(insertModel)
                
                Log.i(TAG, "Pedido creado con éxito en Supabase Nube.")
                withContext(Dispatchers.Main) {
                    onResult(true, "Pedido enviado a cocina.")
                }
                refreshPedidos()
            } catch (e: Exception) {
                Log.e(TAG, "Excepción creando pedido en Supabase", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Error de servicio: ${e.message}")
                }
            }
        }
    }

    // --- ACCIÓN: ACTUALIZAR ESTADO DE UN PEDIDO ---
    fun actualizarEstadoPedido(pedidoId: Long, nuevoEstado: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch {
            // Siempre actualizar la base local de Room primero
            val localPed = dao.getAllPedidos().find { it.id == pedidoId }
            if (localPed != null) {
                val updatedLocal = localPed.copy(
                    estado = nuevoEstado,
                    actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                )
                dao.insertPedido(updatedLocal)
                cargarPedidosDesdeRoom()
                
                // NOTIFICACIÓN SI PASA A LISTO
                if (nuevoEstado == "listo") {
                    enviarNotificacionPedidoListo(updatedLocal.toPedido(converters))
                }
            }

            val client = supabase
            if (client == null) {
                withContext(Dispatchers.Main) {
                    onResult(true, "Estado de pedido #$pedidoId actualizado a '$nuevoEstado' offline.")
                }
                return@launch
            }

            try {
                client.postgrest.from("pedidos").update(mapOf("estado" to nuevoEstado)) {
                    filter {
                        eq("id", pedidoId)
                    }
                }
                
                Log.i(TAG, "Estado de pedido #$pedidoId actualizado a '$nuevoEstado' en Supabase.")
                withContext(Dispatchers.Main) {
                    onResult(true, "Pedido actualizado a $nuevoEstado.")
                }
                refreshPedidos()
            } catch (e: Exception) {
                Log.e(TAG, "Excepción actualizando pedido en Supabase", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Error de red: ${e.message}")
                }
            }
        }
    }

    fun cerrarSocket() {
        Log.i(TAG, "Cerrando Realtime SDK y liberando tareas en segundo plano.")
        realtimeJob?.cancel()
    }

    private fun generarSimulacionCocinaOffline(nuevoId: Long) {
        scope.launch {
            // Pasa a 'cocinando' en 8s
            delay(8000)
            dao.getAllPedidos().find { it.id == nuevoId }?.let { ped ->
                if (ped.estado == "pendiente") {
                    dao.insertPedido(ped.copy(estado = "cocinando"))
                    cargarPedidosDesdeRoom()
                }
            }

            // Pasa a 'listo' en 12s
            delay(12000)
            dao.getAllPedidos().find { it.id == nuevoId }?.let { ped ->
                if (ped.estado == "cocinando") {
                    dao.insertPedido(ped.copy(estado = "listo"))
                    cargarPedidosDesdeRoom()
                }
            }

            // Pasa a 'entregado' en 10s
            delay(10000)
            dao.getAllPedidos().find { it.id == nuevoId }?.let { ped ->
                if (ped.estado == "listo") {
                    dao.insertPedido(ped.copy(estado = "entregado"))
                    cargarPedidosDesdeRoom()
                }
            }
        }
    }

    private fun inicializarMockDataEnRoom() {
        scope.launch {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
            
            val mock1 = Pedido(
                id = 1L,
                mesa = "Mesa 3",
                mesero = "Carlos Gómez",
                items = listOf(
                    ItemPedido("Hamburguesa Premium", 2, 12.50, "Una sin cebolla"),
                    ItemPedido("Papas Fritas", 1, 4.00),
                    ItemPedido("Refresco Sabor Cola", 2, 2.50)
                ),
                total = 31.50,
                estado = "pendiente",
                creado_en = dateStr,
                actualizado_en = dateStr
            )

            val mock2 = Pedido(
                id = 2L,
                mesa = "Mesa 7",
                mesero = "María Rojas",
                items = listOf(
                    ItemPedido("Pizza Personal Pepperoni", 1, 15.00, "Borde doble queso"),
                    ItemPedido("Té Frío Limón", 1, 3.00)
                ),
                total = 18.00,
                estado = "cocinando",
                creado_en = dateStr,
                actualizado_en = dateStr
            )

            dao.insertPedido(PedidoEntity.fromPedido(mock1, 1L, converters))
            dao.insertPedido(PedidoEntity.fromPedido(mock2, 2L, converters))
            cargarPedidosDesdeRoom()
        }
    }
}
