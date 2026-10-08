package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parties")
data class Party(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val phone: String,
    val type: PartyType,
    val address: String = "",
    val notes: String = "",
    val openingBalance: Long = 0L, // In smallest unit (UGX)
    val creditLimit: Long = 0L,    // In smallest unit (0 = no limit)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
