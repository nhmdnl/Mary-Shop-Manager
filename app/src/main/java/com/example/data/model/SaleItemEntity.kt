package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("saleId"),
        Index("productId"),
        Index("date")
    ]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val saleId: Long,
    val invoiceNumber: String,
    val productId: Long,
    val productName: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val costPrice: Long,
    val unitPrice: Long,
    val subtotal: Long,
    val profit: Long,
    val priceTier: PriceTier = PriceTier.RETAIL,
    val date: Long = System.currentTimeMillis()
)
