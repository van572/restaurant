package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// --------------------------------------------------------------------
// MODELO DE DATOS EN ESTRICTA RELACIÓN CON EL ESQUEMA POSTGRES
// --------------------------------------------------------------------
data class ItemPedido(
    val producto: String,
    val cantidad: Int,
    val precio: Double,
    val notas: String = ""
)

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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val TAG = "PedidoRepository"

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            if (isSupabaseConfigured) {
                requestBuilder
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer $supabaseAnonKey")
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    // Configuración obtenida via BuildConfig (.env / AI Studio Secrets)
    private val supabaseUrl = BuildConfig.SUPABASE_URL.trim()
    private val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotEmpty() && 
                supabaseUrl != "YOUR_SUPABASE_URL" && 
                supabaseAnonKey.isNotEmpty() && 
                supabaseAnonKey != "YOUR_SUPABASE_ANON_KEY"

    private fun getCleanRestUrl(): String {
        var base = supabaseUrl.trim()
        
        // Limpieza de sufijos comunes si el usuario ingresó la URL de tabla directamente
        if (base.endsWith("/pedidos")) {
            base = base.substringBefore("/pedidos")
        } else if (base.endsWith("/pedidos/")) {
            base = base.substringBefore("/pedidos/")
        }

        if (base.endsWith("/rest/v1")) {
            base = base.substringBefore("/rest/v1")
        } else if (base.endsWith("/rest/v1/")) {
            base = base.substringBefore("/rest/v1/")
        }

        if (!base.endsWith("/")) {
            base = "$base/"
        }
        
        return "${base}rest/v1/"
    }

    private fun getCleanWsUrl(): String {
        try {
            var base = supabaseUrl.trim()
            if (base.endsWith("/pedidos")) {
                base = base.substringBefore("/pedidos")
            } else if (base.endsWith("/pedidos/")) {
                base = base.substringBefore("/pedidos/")
            }
            if (base.endsWith("/rest/v1")) {
                base = base.substringBefore("/rest/v1")
            } else if (base.endsWith("/rest/v1/")) {
                base = base.substringBefore("/rest/v1/")
            }

            val uri = java.net.URI(base)
            val host = uri.host ?: base
                .replace("https://", "")
                .replace("http://", "")
                .split("/")[0]
            return "wss://$host/realtime/v1/websocket?apikey=$supabaseAnonKey&vsn=1.0.0"
        } catch (e: Exception) {
            val rawHost = supabaseUrl
                .replace("https://", "")
                .replace("http://", "")
                .split("/")[0]
            return "wss://$rawHost/realtime/v1/websocket?apikey=$supabaseAnonKey&vsn=1.0.0"
        }
    }

    // Estado reactivo expuesto a la UI
    private val _pedidos = MutableStateFlow<List<Pedido>>(emptyList())
    val pedidos: StateFlow<List<Pedido>> = _pedidos.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionType.MOCK_DEMO)
    val connectionState: StateFlow<ConnectionType> = _connectionState.asStateFlow()

    private val _isConnectingWS = MutableStateFlow(false)
    val isConnectingWS: StateFlow<Boolean> = _isConnectingWS.asStateFlow()

    // Integración de Moshi para serializar/deserializar de forma estricta y limpia
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, Pedido::class.java)
    private val listAdapter = moshi.adapter<List<Pedido>>(listType)
    private val singleAdapter = moshi.adapter(Pedido::class.java)
    private val insertAdapter = moshi.adapter(PedidoInsert::class.java)

    // Almacenamiento local mock en memoria
    private val mockPedidos = mutableListOf<Pedido>()
    private var mockIdCounter = 100L

    private var ws: WebSocket? = null
    private var heartbeatJob: Job? = null

    init {
        // Inicializar datos simulados de demostración si arranca en modo local
        if (!isSupabaseConfigured) {
            Log.i(TAG, "Arrancando en modo DEMO local (Supabase no configurado en credenciales).")
            _connectionState.value = ConnectionType.MOCK_DEMO
            inicializarMockData()
        } else {
            Log.i(TAG, "Supabase detectado de forma válida. Sincronizando en la Nube mediante Polling continuo.")
            _connectionState.value = ConnectionType.NUBE
            refreshPedidos()
        }

        // Polling constante y automático de pedidos cada 3-4 segundos para sincronizar Mesero y Cocina de forma segura
        iniciarPollingDePedidos()
    }

    // --- ACCIÓN: ACTUALIZAR PEDIDOS (GET /REST/V1/PEDIDOS) ---
    fun refreshPedidos() {
        if (!isSupabaseConfigured) {
            _pedidos.value = mockPedidos.filter { it.estado != "pagado" }
            return
        }

        scope.launch {
            try {
                _isConnectingWS.value = true
                // Filtrar para no traer pedidos ya pagados/liquidados por la caja
                val cleanUrl = getCleanRestUrl()
                val requestUrl = "${cleanUrl}pedidos?estado=neq.pagado&select=*&order=creado_en.asc"
                
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    _isConnectingWS.value = false
                    if (!response.isSuccessful) throw IOException("Error http: ${response.code}")
                    val bodyString = response.body?.string() ?: ""
                    val result = listAdapter.fromJson(bodyString)
                    _pedidos.value = result ?: emptyList()
                    Log.d(TAG, "Pedidos actualizados exitosamente de Supabase. Total: ${_pedidos.value.size}")
                }
            } catch (e: Exception) {
                _isConnectingWS.value = false
                Log.e(TAG, "Error refrescando pedidos en la nube, usando fallback mock local", e)
                // Fallback temporal si la conexión falla a mitad del servicio
                _pedidos.value = mockPedidos.filter { it.estado != "pagado" }
            }
        }
    }

    // --- ACCIÓN: CREAR UN NUEVO PEDIDO (POST /REST/V1/PEDIDOS) ---
    fun crearPedido(pedido: Pedido, onResult: (Boolean, String?) -> Unit) {
        if (!isSupabaseConfigured) {
            // Operar simulador local
            val nuevoId = mockIdCounter++
            val nuevoPedido = pedido.copy(
                id = nuevoId,
                creado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
            )
            mockPedidos.add(nuevoPedido)
            _pedidos.value = mockPedidos.filter { it.estado != "pagado" }
            onResult(true, "Pedido simulado creado localmente (Mesa: ${pedido.mesa})")

            // Simular flujo automático en la cocina (KDS) para pruebas dinámicas en tiempo real
            scope.launch {
                // 1. Pasa a 'cocinando' después de 8 segundos
                delay(8000)
                val idx1 = mockPedidos.indexOfFirst { it.id == nuevoId }
                if (idx1 != -1 && mockPedidos[idx1].estado == "pendiente") {
                    mockPedidos[idx1] = mockPedidos[idx1].copy(
                        estado = "cocinando",
                        actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                    )
                    _pedidos.value = mockPedidos.filter { it.estado != "pagado" }
                }

                // 2. Pasa a 'listo' después de 12 segundos más
                delay(12000)
                val idx2 = mockPedidos.indexOfFirst { it.id == nuevoId }
                if (idx2 != -1 && mockPedidos[idx2].estado == "cocinando") {
                    mockPedidos[idx2] = mockPedidos[idx2].copy(
                        estado = "listo",
                        actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                    )
                    _pedidos.value = mockPedidos.filter { it.estado != "pagado" }
                }

                // 3. Pasa a 'entregado' después de 10 segundos más
                delay(10000)
                val idx3 = mockPedidos.indexOfFirst { it.id == nuevoId }
                if (idx3 != -1 && mockPedidos[idx3].estado == "listo") {
                    mockPedidos[idx3] = mockPedidos[idx3].copy(
                        estado = "entregado",
                        actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                    )
                    _pedidos.value = mockPedidos.filter { it.estado != "pagado" }
                }
            }
            return
        }

        scope.launch {
            try {
                val cleanUrl = getCleanRestUrl()
                val requestUrl = "${cleanUrl}pedidos"

                // Map into PedidoInsert to strip id, creado_en, and actualizado_en fields
                val insertModel = PedidoInsert(
                    mesa = pedido.mesa,
                    mesero = pedido.mesero,
                    items = pedido.items,
                    total = pedido.total,
                    estado = pedido.estado
                )
                val jsonStr = insertAdapter.toJson(insertModel)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val reqBody = jsonStr.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(requestUrl)
                    .post(reqBody)
                    .addHeader("Prefer", "return=representation")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Pedido creado con éxito en Supabase Nube.")
                        withContext(Dispatchers.Main) {
                            onResult(true, "Pedido enviado a cocina.")
                        }
                        refreshPedidos()
                    } else {
                        val bodyErr = response.body?.string() ?: ""
                        Log.e(TAG, "Error insertando pedido en Supabase: $bodyErr")
                        val customMessage = if (response.code == 404) {
                            "Error 404: La tabla 'pedidos' no existe en tu base de datos de Supabase. \n\nAsegúrate de ingresar a tu consola de Supabase, abrir el 'SQL Editor' y copiar y ejecutar el archivo 'database/esquema.sql' para crear la tabla 'pedidos'."
                        } else {
                            "Error de servicio: ${response.code}\n$bodyErr"
                        }
                        withContext(Dispatchers.Main) {
                            onResult(false, customMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción creando pedido en Supabase", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Error de red: ${e.message}")
                }
            }
        }
    }

    // --- ACCIÓN: ACTUALIZAR ESTADO DE UN PEDIDO (PATCH /REST/V1/PEDIDOS) ---
    fun actualizarEstadoPedido(pedidoId: Long, nuevoEstado: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (!isSupabaseConfigured) {
            val idx = mockPedidos.indexOfFirst { it.id == pedidoId }
            if (idx != -1) {
                mockPedidos[idx] = mockPedidos[idx].copy(
                    estado = nuevoEstado,
                    actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                )
                _pedidos.value = mockPedidos.filter { it.estado != "pagado" }
                onResult(true, "Estado simulado de pedido #$pedidoId actualizado a '$nuevoEstado'")
            } else {
                onResult(false, "Pedido no encontrado")
            }
            return
        }

        scope.launch {
            try {
                val cleanUrl = getCleanRestUrl()
                val requestUrl = "${cleanUrl}pedidos?id=eq.$pedidoId"

                // Crear el body JSON para PATCH: {"estado": "cocinando"}
                val jsonStr = "{\"estado\": \"$nuevoEstado\"}"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val reqBody = jsonStr.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(requestUrl)
                    .patch(reqBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Estado de pedido #$pedidoId actualizado a '$nuevoEstado' con éxito.")
                        withContext(Dispatchers.Main) {
                            onResult(true, "Pedido actualizado a $nuevoEstado.")
                        }
                        refreshPedidos()
                    } else {
                        val bodyErr = response.body?.string() ?: ""
                        Log.e(TAG, "Error actualizando pedido en Supabase: $bodyErr")
                        withContext(Dispatchers.Main) {
                            onResult(false, "Error de servicio: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción actualizando pedido en Supabase", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Error de red: ${e.message}")
                }
            }
        }
    }

    // --- ENLACE: POLING AUTOMÁTICO EN SEGUNDO PLANO PARA ESCUCHAR CAMBIOS ---
    private var pollingJob: Job? = null

    fun iniciarPollingDePedidos() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    // Solo consultamos si está configurado para la nube, o forzamos sincronía local
                    refreshPedidos()
                } catch (e: Exception) {
                    Log.e(TAG, "Error en bucle de polling de pedidos", e)
                }
                delay(3000) // Encuesta rápida cada 3 segundos
            }
        }
    }

    fun detenerPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun cerrarSocket() {
        Log.i(TAG, "Cerrando recursos y cancelando polling periódico de pedidos.")
        detenerPolling()
    }

    // --- FUNCIONES INTERNAS DE CARGA LOCAL (MOCK DATA FOR TESTING) ---
    private fun inicializarMockData() {
        mockPedidos.add(
            Pedido(
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
                creado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date() - Int.MAX_VALUE),
                actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date() - Int.MAX_VALUE)
            )
        )
        mockPedidos.add(
            Pedido(
                id = 2L,
                mesa = "Mesa 7",
                mesero = "María Rojas",
                items = listOf(
                    ItemPedido("Pizza Personal Pepperoni", 1, 15.00, "Borde doble queso"),
                    ItemPedido("Té Frío Limón", 1, 3.00)
                ),
                total = 18.00,
                estado = "cocinando",
                creado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                actualizado_en = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
            )
        )
        _pedidos.value = mockPedidos
    }

    // Extensión simple para restar fechas simuladas
    private operator fun java.util.Date.minus(days: Int): java.util.Date {
        return java.util.Date(this.time - days * 1000L)
    }
}
