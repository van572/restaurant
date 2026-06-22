package com.example.data

import android.content.Context
import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

class PedidoRoomConverters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val type = Types.newParameterizedType(List::class.java, ItemPedido::class.java)
    private val adapter = moshi.adapter<List<ItemPedido>>(type)

    fun fromItemsList(value: List<ItemPedido>?): String {
        return value?.let { adapter.toJson(it) } ?: "[]"
    }

    fun toItemsList(value: String?): List<ItemPedido> {
        return value?.let { adapter.fromJson(it) } ?: emptyList()
    }
}

@Entity(tableName = "pedidos")
data class PedidoEntity(
    @PrimaryKey val id: Long,
    val mesa: String,
    val mesero: String,
    val itemsJson: String,
    val total: Double,
    val estado: String,
    val creado_en: String?,
    val actualizado_en: String?
) {
    fun toPedido(converters: PedidoRoomConverters): Pedido {
        return Pedido(
            id = id,
            mesa = mesa,
            mesero = mesero,
            items = converters.toItemsList(itemsJson),
            total = total,
            estado = estado,
            creado_en = creado_en,
            actualizado_en = actualizado_en
        )
    }

    companion object {
        fun fromPedido(pedido: Pedido, id: Long, converters: PedidoRoomConverters): PedidoEntity {
            return PedidoEntity(
                id = id,
                mesa = pedido.mesa,
                mesero = pedido.mesero,
                itemsJson = converters.fromItemsList(pedido.items),
                total = pedido.total,
                estado = pedido.estado,
                creado_en = pedido.creado_en,
                actualizado_en = pedido.actualizado_en
            )
        }
    }
}

@Entity(tableName = "inventario")
data class InventarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val categoria: String,
    val stock: Double,
    val barcode: String?,
    val unidadMedida: String
)

@Dao
interface PedidoDao {
    @Query("SELECT * FROM pedidos ORDER BY id ASC")
    fun getAllPedidosFlow(): Flow<List<PedidoEntity>>

    @Query("SELECT * FROM pedidos ORDER BY id ASC")
    suspend fun getAllPedidos(): List<PedidoEntity>

    @Query("SELECT * FROM pedidos WHERE id = :id LIMIT 1")
    suspend fun getPedidoById(id: Long): PedidoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPedido(pedido: PedidoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPedidos(pedidos: List<PedidoEntity>)

    @Query("DELETE FROM pedidos")
    suspend fun clearAll()

    @Query("DELETE FROM pedidos WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Inventario
    @Query("SELECT * FROM inventario")
    suspend fun getAllInventario(): List<InventarioEntity>

    @Query("SELECT * FROM inventario WHERE id = :id LIMIT 1")
    suspend fun getInventarioById(id: Long): InventarioEntity?

    @Query("SELECT * FROM inventario WHERE barcode = :barcode LIMIT 1")
    suspend fun getInventarioByBarcode(barcode: String): InventarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventario(item: InventarioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventarioItems(items: List<InventarioEntity>)
}

@Database(entities = [PedidoEntity::class, InventarioEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pedidoDao(): PedidoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "restaurante_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
