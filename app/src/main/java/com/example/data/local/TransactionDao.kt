package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE partyId = :partyId ORDER BY date ASC, id ASC")
    fun getTransactionsForParty(partyId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE partyId = :partyId AND isDeleted = 0 ORDER BY date ASC, id ASC")
    suspend fun getActiveTransactionsForPartyDirect(partyId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND date BETWEEN :startTime AND :endTime ORDER BY date DESC")
    fun getActiveTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE partyId = :partyId AND isDeleted = 0 AND date BETWEEN :startTime AND :endTime ORDER BY date ASC, id ASC")
    fun getActiveTransactionsForPartyBetween(partyId: Long, startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE partyId = :partyId AND isDeleted = 0 AND date BETWEEN :startTime AND :endTime ORDER BY date ASC, id ASC")
    suspend fun getActiveTransactionsForPartyBetweenDirect(partyId: Long, startTime: Long, endTime: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE partyId = :partyId AND isDeleted = 0 AND date < :beforeDate ORDER BY date ASC, id ASC")
    suspend fun getActiveTransactionsForPartyBeforeDirect(partyId: Long, beforeDate: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC, id DESC")
    fun getActiveTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC, id DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isDeleted = 1, deletedAt = :deletedAt, deleteReason = :reason WHERE id = :id")
    suspend fun softDeleteTransaction(id: Long, deletedAt: Long, reason: String?)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()
}
