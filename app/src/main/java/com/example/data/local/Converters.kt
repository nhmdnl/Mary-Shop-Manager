package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.PartyType
import com.example.data.model.PaymentMethod
import com.example.data.model.StockMovementType
import com.example.data.model.TransactionType

class Converters {
    @TypeConverter
    fun fromPartyType(value: PartyType?): String? = value?.name

    @TypeConverter
    fun toPartyType(value: String?): PartyType? =
        value?.let { runCatching { PartyType.valueOf(it) }.getOrDefault(PartyType.CUSTOMER) }

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? =
        value?.let { runCatching { TransactionType.valueOf(it) }.getOrDefault(TransactionType.SALE_ON_CREDIT) }

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod? =
        value?.let { runCatching { PaymentMethod.valueOf(it) }.getOrDefault(PaymentMethod.CASH) }

    @TypeConverter
    fun fromStockMovementType(value: StockMovementType?): String? = value?.name

    @TypeConverter
    fun toStockMovementType(value: String?): StockMovementType? =
        value?.let { runCatching { StockMovementType.valueOf(it) }.getOrDefault(StockMovementType.PURCHASE_IN) }
}
