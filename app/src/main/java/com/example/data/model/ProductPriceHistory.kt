package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_price_history",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("productId"),
        Index("date")
    ]
)
data class ProductPriceHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val costPrice: Long,
    val retailPrice: Long,
    val wholesalePrice: Long,
    val previousCostPrice: Long? = null,
    val previousRetailPrice: Long? = null,
    val previousWholesalePrice: Long? = null,
    val changeReason: String = "Price update",
    val date: Long = System.currentTimeMillis()
)
