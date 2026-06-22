package com.example.data

import kotlinx.serialization.Serializable

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
    val estado: String = "pendiente",
    val creado_en: String? = null,
    val actualizado_en: String? = null
)

@Serializable
data class MenuPlatillo(
    val nombre: String,
    val precio: Double,
    val categoria: String,
    val descripcion: String = "",
    val emoji: String = "🍔",
    val esPorPeso: Boolean = false,
    val inventarioDependienteId: Long? = null
)

@Serializable
enum class CategoriaPlatillo(val etiqueta: String) {
    DESAYUNO("Desayuno 🍳"),
    ALMUERZO("Almuerzo 🍲"),
    CENA("Cena 🌙"),
    BEBIDA("Bebidas 🍹"),
    ACOMPANAMIENTO("Extras 🍟"),
    POSTRE("Postres 🍰")
}

@Serializable
enum class InventarioCategoria(val etiqueta: String) {
    LICORES("Licores"),
    ALIMENTO("Alimentos"),
    CARNE("Carne")
}

@Serializable
data class InventarioItem(
    val id: Long? = null,
    val nombre: String,
    val categoria: InventarioCategoria,
    val stock: Double,
    val barcode: String? = null,
    val unidadMedida: String = "un"
)

// Helper class for local cart management
@Serializable
data class ItemCart(
    val platillo: MenuPlatillo,
    val cantidad: Int,
    val notas: String = ""
)

fun ItemCart.toItemPedido() = ItemPedido(
    producto = this.platillo.nombre,
    cantidad = this.cantidad,
    precio = this.platillo.precio,
    notas = this.notas
)

val MENU_ITEMS = listOf(
    MenuPlatillo("Parrilla Mixta", 25.00, "ALMUERZO", "Solomo, pollo y chorizo.", "🥩", esPorPeso = true, inventarioDependienteId = 3),
    MenuPlatillo("Hamburguesa Premium", 12.50, "CENA", "Queso cheddar, tocino, aderezo gourmet.", "🍔", inventarioDependienteId = 3),
    MenuPlatillo("Pollo a la Brasa", 15.00, "ALMUERZO", "Pollo entero con papas.", "🍗", inventarioDependienteId = 4),
    MenuPlatillo("Tacos de Res (x3)", 8.50, "CENA", "Cebollitas asadas, cilantro, salsas.", "🌮", inventarioDependienteId = 3),
    MenuPlatillo("Papas Fritas", 4.00, "ACOMPAÑAMIENTO", "Doraditas y crujientes con sal marina.", "🍟"),
    MenuPlatillo("Cerveza Polar Bottle", 3.00, "BEBIDA", "Cerveza de botella fría.", "🍺", inventarioDependienteId = 2),
    MenuPlatillo("Whisky 18 On The Rocks", 15.00, "BEBIDA", "Servicio de Whisky.", "🥃", inventarioDependienteId = 1),
    MenuPlatillo("Té Frío Limón", 3.00, "BEBIDA", "Infusión de té negro con zumo fresco.", "🍹"),
    MenuPlatillo("Refresco Sabor Cola", 2.50, "BEBIDA", "Vaso grande con hielo y limón.", "🥤"),
    MenuPlatillo("Agua Mineral", 2.00, "BEBIDA", "Agua gasificada purificada fría.", "💧")
)

fun saveMenuToPrefs(sharedPrefs: android.content.SharedPreferences, list: List<MenuPlatillo>) {
    val serialized = list.joinToString("###") { p ->
        "${p.nombre}||${p.precio}||${p.categoria}||${p.descripcion}||${p.emoji}||${p.esPorPeso}||${p.inventarioDependienteId ?: ""}"
    }
    sharedPrefs.edit().putString("custom_menu_items", serialized).apply()
}

fun saveCategoriesToPrefs(sharedPrefs: android.content.SharedPreferences, categories: List<String>) {
    sharedPrefs.edit().putString("custom_categories", categories.joinToString(",")).apply()
}

fun loadCategoriesFromPrefs(sharedPrefs: android.content.SharedPreferences): List<String> {
    val raw = sharedPrefs.getString("custom_categories", null) ?: return listOf("DESAYUNO", "ALMUERZO", "CENA", "BEBIDA", "ACOMPAÑAMIENTO", "POSTRE")
    return if (raw.isBlank()) emptyList() else raw.split(",")
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
                categoria = parts[2],
                descripcion = if (parts.size > 3) parts[3] else "",
                emoji = if (parts.size > 4) parts[4] else "🍔",
                esPorPeso = if (parts.size > 5) parts[5].toBoolean() else false,
                inventarioDependienteId = if (parts.size > 6 && parts[6].isNotBlank()) parts[6].toLong() else null
            )
        }
    } catch (e: Exception) {
        null
    }
}
