package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConnectionType
import com.example.data.ItemPedido
import com.example.data.Pedido
import com.example.data.PedidoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeseroViewModel(
    private val repository: PedidoRepository
) : ViewModel() {

    private val TAG = "MeseroViewModel"

    // Observe flows from Repository
    val pedidos = repository.pedidos
    val connectionState = repository.connectionState
    val isConnectingWS = repository.isConnectingWS

    // Role-based state
    private val _userRole = MutableStateFlow("mesero") // "mesero" || "cocinero"
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    // Navigation and UX states
    private val _activeTab = MutableStateFlow("mesas") // "mesas", "menu", "pedidos"
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    private val _categoriaSeleccionada = MutableStateFlow(CategoriaPlatillo.COMIDA)
    val categoriaSeleccionada: StateFlow<CategoriaPlatillo> = _categoriaSeleccionada.asStateFlow()

    private val _mesaSeleccionada = MutableStateFlow("Mesa 1")
    val mesaSeleccionada: StateFlow<String> = _mesaSeleccionada.asStateFlow()

    private val _cocineroFiltroEstado = MutableStateFlow("todos")
    val cocineroFiltroEstado: StateFlow<String> = _cocineroFiltroEstado.asStateFlow()

    // Dynamic Food Items List
    private val _menuPlatillos = MutableStateFlow<List<MenuPlatillo>>(emptyList())
    val menuPlatillos: StateFlow<List<MenuPlatillo>> = _menuPlatillos.asStateFlow()

    // Comanda / Order Draft (Carrito)
    private val _carrito = MutableStateFlow<List<ItemCart>>(emptyList())
    val carrito: StateFlow<List<ItemCart>> = _carrito.asStateFlow()

    // Global loading and progress states
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun setUserRole(role: String) {
        _userRole.value = role
    }

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    fun setCategoriaSeleccionada(cat: CategoriaPlatillo) {
        _categoriaSeleccionada.value = cat
    }

    fun setMesaSeleccionada(mesa: String) {
        _mesaSeleccionada.value = mesa
    }

    fun setCocineroFiltroEstado(filt: String) {
        _cocineroFiltroEstado.value = filt
    }

    // --- MANEJO DEL MENU DINÁMICO ---
    fun loadMenu(sharedPrefs: SharedPreferences) {
        val localLoaded = loadMenuFromPrefs(sharedPrefs)
        if (localLoaded != null) {
            _menuPlatillos.value = localLoaded
        } else {
            _menuPlatillos.value = MENU_ITEMS
        }

        // Intenta refrescar el menú dinámica en la nube si está configurado en Supabase
        if (repository.isSupabaseConfigured) {
            Log.i(TAG, "Cargando menú central y unificado desde la tabla Supabase 'menu'...")
            repository.fetchDynamicMenu { cloudMenu ->
                if (cloudMenu != null) {
                    Log.i(TAG, "¡Menú dinámico de Supabase obtenido con éxito! Centralizando.")
                    _menuPlatillos.value = cloudMenu
                    saveMenuToPrefs(sharedPrefs, cloudMenu)
                }
            }
        }
    }

    fun updateMenuPlatillo(sharedPrefs: SharedPreferences, oldPlatillo: MenuPlatillo, newPlatillo: MenuPlatillo) {
        val current = _menuPlatillos.value.toMutableList()
        val index = current.indexOfFirst { it.nombre == oldPlatillo.nombre }
        if (index != -1) {
            current[index] = newPlatillo
        } else {
            current.add(newPlatillo)
        }
        _menuPlatillos.value = current
        saveMenuToPrefs(sharedPrefs, current)
    }

    fun resetMenuToDefault(sharedPrefs: SharedPreferences) {
        _menuPlatillos.value = MENU_ITEMS
        saveMenuToPrefs(sharedPrefs, MENU_ITEMS)
    }

    // --- ACCIONES SOBRE EL CARRITO DE PEDIDO ---
    fun agregarAlCarrito(platillo: MenuPlatillo) {
        val current = _carrito.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.platillo.nombre == platillo.nombre }
        if (existingIndex != -1) {
            val existing = current[existingIndex]
            current[existingIndex] = existing.copy(cantidad = existing.cantidad + 1)
        } else {
            current.add(ItemCart(platillo, 1))
        }
        _carrito.value = current
    }

    fun restarDelCarrito(index: Int) {
        val current = _carrito.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            if (item.cantidad > 1) {
                current[index] = item.copy(cantidad = item.cantidad - 1)
            } else {
                current.removeAt(index)
            }
        }
        _carrito.value = current
    }

    fun sumarAlCarrito(index: Int) {
        val current = _carrito.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(cantidad = item.cantidad + 1)
        }
        _carrito.value = current
    }

    fun actualizarNotasItem(index: Int, notas: String) {
        val current = _carrito.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(notas = notas)
        }
        _carrito.value = current
    }

    fun vaciarCarrito() {
        _carrito.value = emptyList()
    }

    // --- CREACIÓN / ENVÍO DE ORDENES ---
    fun enviarPedido(mesero: String, onCompleted: (Boolean, String?) -> Unit) {
        if (_carrito.value.isEmpty()) {
            onCompleted(false, "El carrito de comanda está vacío")
            return
        }

        _isSending.value = true
        val itemsMapped = _carrito.value.map { it.toItemPedido() }
        val total = _carrito.value.sumOf { it.platillo.precio * it.cantidad }
        val finalPedido = Pedido(
            mesa = _mesaSeleccionada.value,
            mesero = if (mesero.isNotBlank()) mesero else "Mesero Móvil",
            items = itemsMapped,
            total = total,
            estado = "pendiente"
        )

        repository.crearPedido(finalPedido) { success, message ->
            _isSending.value = false
            if (success) {
                vaciarCarrito()
            }
            onCompleted(success, message)
        }
    }

    fun actualizarEstadoPedido(id: Long, nuevoEstado: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        repository.actualizarEstadoPedido(id, nuevoEstado, onResult)
    }

    fun refreshPedidos() {
        repository.refreshPedidos()
    }
}
