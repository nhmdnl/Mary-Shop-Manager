package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ProductPriceHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPriceHistoryDao {
    @Query("SELECT * FROM product_price_history WHERE productId = :productId ORDER BY date DESC")
    fun getPriceHistoryForProduct(productId: Long): Flow<List<ProductPriceHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ProductPriceHistory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(historyList: List<ProductPriceHistory>)

    @Query("DELETE FROM product_price_history WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)
}
