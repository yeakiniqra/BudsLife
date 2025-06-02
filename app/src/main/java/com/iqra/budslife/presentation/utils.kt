package com.iqra.budslife.presentation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


fun formatDate(date: Date): String {
    val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return format.format(date)
}

