package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val fullDateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun formatDateTime(timestamp: Long): String {
        return fullDateTimeFormat.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        return shortDateFormat.format(Date(timestamp))
    }

    fun formatRelative(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val day = 24 * 60 * 60 * 1000L

        return when {
            diff < 60 * 1000L -> "Just now"
            diff < day && SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp)) ==
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now)) ->
                "Today, ${timeFormat.format(Date(timestamp))}"
            diff < 2 * day -> "Yesterday, ${timeFormat.format(Date(timestamp))}"
            else -> shortDateFormat.format(Date(timestamp))
        }
    }
}
