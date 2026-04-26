package com.example.androidhack.models

data class PromoCode(
    val id: String = "",
    val code: String = "",
    val type: String = "Percentage", // "Percentage" or "Flat"
    val value: Double = 0.0,
    val isActive: Boolean = true
)
