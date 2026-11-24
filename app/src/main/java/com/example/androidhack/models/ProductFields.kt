package com.example.androidhack.models

import kotlin.math.round

object ProductFields {
    val SIZES = listOf("UK 6", "UK 7", "UK 8", "UK 9", "UK 10")

    fun parsePrice(text: String): Double {
        val clean = text.replace(",", "")
        val match = Regex("\\d+(?:\\.\\d+)?").find(clean)
        return match?.value?.toDoubleOrNull() ?: 0.0
    }

    fun finalPrice(price: Double, discountPercent: Int): Double {
        val discount = discountPercent.coerceIn(0, 100)
        val discounted = price * (1.0 - discount / 100.0)
        return round(discounted)
    }

    fun splitTags(text: String): List<String> {
        return text.split(',', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

