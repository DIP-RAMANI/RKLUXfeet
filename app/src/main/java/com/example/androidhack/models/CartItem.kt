package com.example.androidhack.models

data class CartItem(
    val id: String = "",
    val name: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val quantity: Long = 1L
)
