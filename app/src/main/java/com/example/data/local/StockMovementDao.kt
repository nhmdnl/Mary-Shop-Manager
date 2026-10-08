package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StockMovement
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY date DESC")
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements ORDER BY date DESC LIMIT :limit")
    fun getRecentMovements(limit: Int = 50): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getMovementsBetween(startTime: Long, endTime: Long): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    suspend fun getMovementsBetweenDirect(startTime: Long, endTime: Long): List<StockMovement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<StockMovement>): List<Long>

    @Query("DELETE FROM stock_movements WHERE productId = :productId")
    suspend fun deleteMovementsForProduct(productId: Long)

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAllMovements()
}
