package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.Party
import com.example.data.model.Product
import com.example.data.model.ProductPriceHistory
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.StockMovement
import com.example.data.model.TransactionEntity

@Database(
    entities = [
        Party::class,
        TransactionEntity::class,
        Product::class,
        StockMovement::class,
        ProductPriceHistory::class,
        SaleEntity::class,
        SaleItemEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun partyDao(): PartyDao
    abstract fun transactionDao(): TransactionDao
    abstract fun productDao(): ProductDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun productPriceHistoryDao(): ProductPriceHistoryDao
    abstract fun saleDao(): SaleDao

    companion object {
        const val DATABASE_NAME = "mary_shop_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun checkpointDatabase() {
            INSTANCE?.let { db ->
                runCatching {
                    val query = androidx.sqlite.db.SimpleSQLiteQuery("pragma wal_checkpoint(full)")
                    val cursor = db.openHelper.writableDatabase.query(query)
                    cursor.close()
                }
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    if (db.isOpen) {
                        db.close()
                    }
                }
                INSTANCE = null
            }
        }
    }
}
