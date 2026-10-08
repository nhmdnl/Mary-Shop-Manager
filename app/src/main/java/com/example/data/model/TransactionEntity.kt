package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["partyId"]),
        Index(value = ["date"]),
        Index(value = ["isDeleted"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val partyId: Long,
    val type: TransactionType,
    val amount: Long, // Stored in smallest currency unit (always positive Long)
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val referenceNo: String = "",
    val note: String = "",
    val adjustmentDirection: Int = 1, // 1 for increase amount owed, -1 for decrease amount owed
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val deleteReason: String? = null,
    val isReversal: Boolean = false,
    val reversedTransactionId: Long? = null,
    val replacementTransactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
