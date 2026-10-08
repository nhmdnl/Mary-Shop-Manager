package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SaleEntity
import com.example.data.model.SaleItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>): List<Long>

    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getSalesBetween(startTime: Long, endTime: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    suspend fun getSalesBetweenDirect(startTime: Long, endTime: Long): List<SaleEntity>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getItemsForSale(saleId: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE date BETWEEN :startTime AND :endTime")
    fun getSaleItemsBetween(startTime: Long, endTime: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE date BETWEEN :startTime AND :endTime")
    suspend fun getSaleItemsBetweenDirect(startTime: Long, endTime: Long): List<SaleItemEntity>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: Long): SaleEntity?

    @Query("SELECT * FROM sales WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getSaleByInvoiceNumber(invoiceNumber: String): SaleEntity?

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getSaleCount(): Int

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()
}
