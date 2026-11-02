package com.adochi.ui

import java.util.Locale

/** 950 → "950", 12_400 → "12.4K", 3_200_000 → "3.2M". */
fun formatCount(value: Long): String = when {
    value < 10_000 -> String.format(Locale.US, "%,d", value)
    value < 1_000_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0).replace(".0K", "K")
    else -> String.format(Locale.US, "%.1fM", value / 1_000_000.0).replace(".0M", "M")
}

fun formatCount(value: Int): String = formatCount(value.toLong())

fun formatAgo(time: Long, now: Long = System.currentTimeMillis()): String {
    if (time == 0L) return "never"
    val minutes = (now - time) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
