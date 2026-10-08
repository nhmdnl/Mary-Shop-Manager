package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("sku"),
        Index("category"),
        Index("supplierId")
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sku: String = "",
    val category: String = "General",
    val unit: String = "pcs",
    val costPrice: Long = 0L,
    val retailPrice: Long = 0L,
    val wholesalePrice: Long = 0L,
    val stockQty: Double = 0.0,
    val reorderLevel: Double = 5.0,
    val supplierId: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
