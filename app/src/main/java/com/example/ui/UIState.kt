package com.example.ui

import com.example.data.ItemPedido
import com.example.data.Pedido

// 1. ESTRUCTURACIÓN DE ITEM DEL MENÚ
data class MenuPlatillo(
    val nombre: String,
    val precio: Double,
    val categoria: CategoriaPlatillo,
    val descripcion: String = "",
    val emoji: String = "🍔"
)

enum class CategoriaPlatillo(val etiqueta: String) {
    COMIDA("Comida Principal"),
    ACOMPANAMIENTO("Complementos / Entradas"),
    BEBIDA("Bebidas y Jugos")
}

// 2. ITEM DRAFT SELECCIONADO PARA PEDIDO EN CONSTRUCCIÓN
data class ItemCart(
    val platillo: MenuPlatillo,
    val cantidad: Int,
    val notas: String = ""
) {
    fun toItemPedido(): ItemPedido {
        return ItemPedido(
            producto = platillo.nombre,
            cantidad = cantidad,
            precio = platillo.precio,
            notas = notas
        )
    }
}

// 3. ESTADOS DE VISUALIZACIÓN DE LA PANTALLA GENERAL DEL MESERO
sealed class MeseroUiState {
    object Idle : MeseroUiState()
    object EnviandoPedido : MeseroUiState()
    data class ExitoEnviado(val mensaje: String) : MeseroUiState()
    data class ErrorEnviado(val error: String) : MeseroUiState()
}
