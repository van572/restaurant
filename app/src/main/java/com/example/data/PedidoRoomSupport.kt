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
}

@Database(entities = [PedidoEntity::class], version = 1, exportSchema = false)
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
