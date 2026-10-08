package com.example.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.CreditGreenContainer
import com.example.ui.theme.CreditGreenText
import com.example.ui.theme.DebtRed
import com.example.ui.theme.DebtRedContainer
import com.example.ui.theme.DebtRedText
import java.util.Locale

object PricingHelper {
    fun calculateMarginPercent(sellingPrice: Long, costPrice: Long): Double {
        if (sellingPrice <= 0L) return 0.0
        return ((sellingPrice - costPrice).toDouble() / sellingPrice.toDouble()) * 100.0
    }

    fun calculateMarkupPercent(sellingPrice: Long, costPrice: Long): Double {
        if (costPrice <= 0L) return if (sellingPrice > 0) 100.0 else 0.0
        return ((sellingPrice - costPrice).toDouble() / costPrice.toDouble()) * 100.0
    }

    fun calculateProfit(sellingPrice: Long, costPrice: Long): Long {
        return sellingPrice - costPrice
    }

    fun formatPercent(percent: Double, showSign: Boolean = true): String {
        val sign = if (showSign && percent > 0.0) "+" else ""
        return String.format(Locale.US, "$sign%.1f%%", percent)
    }
}

@Composable
fun LivePriceMarginMarkupCard(
    costPrice: Long,
    sellingPrice: Long,
    currency: String,
    title: String = "Live Profit Margin & Markup",
    modifier: Modifier = Modifier
) {
    val profit = PricingHelper.calculateProfit(sellingPrice, costPrice)
    val margin = PricingHelper.calculateMarginPercent(sellingPrice, costPrice)
    val markup = PricingHelper.calculateMarkupPercent(sellingPrice, costPrice)

    val isProfitable = profit > 0
    val isLoss = profit < 0
    val isBreakEven = profit == 0L

    val containerColor = when {
        isLoss -> DebtRedContainer.copy(alpha = 0.5f)
        isProfitable -> CreditGreenContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val primaryTextColor = when {
        isLoss -> DebtRedText
        isProfitable -> CreditGreenText
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )

                if (isLoss) {
                    Surface(
                        color = DebtRedContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DebtRedText, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Selling at a Loss!", style = MaterialTheme.typography.labelSmall, color = DebtRedText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profit per unit
                Column {
                    Text(
                        text = "Unit Profit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(profit, currency, showSign = true),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isLoss) DebtRed else if (isProfitable) CreditGreen else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Margin %
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Gross Margin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = PricingHelper.formatPercent(margin),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isLoss) DebtRed else if (isProfitable) CreditGreen else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Markup %
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Markup",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = PricingHelper.formatPercent(markup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isLoss) DebtRed else if (isProfitable) CreditGreen else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
