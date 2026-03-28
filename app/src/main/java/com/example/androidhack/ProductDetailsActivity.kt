package com.example.androidhack

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProductDetailsActivity : AppCompatActivity() {

    private var isWishlisted = false
    private lateinit var ivHeart: ImageView
    private val db = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    // Size selector state
    private var selectedSize = "UK 7"
    private val sizeButtonIds = listOf(
        R.id.btnSize6 to "UK 6",
        R.id.btnSize7 to "UK 7",
        R.id.btnSize8 to "UK 8",
        R.id.btnSize9 to "UK 9",
        R.id.btnSize10 to "UK 10"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_product_details)

            val productId    = intent.getStringExtra("productId") ?: ""
            val productName  = intent.getStringExtra("productName") ?: "Unknown Product"
            val productPrice = intent.getStringExtra("productPrice") ?: "₹0"
            val productImage = intent.getStringExtra("productImage") ?: ""
            val productDesc  = intent.getStringExtra("productDesc") ?: "No description available."
            val productSpecs = intent.getStringExtra("productSpecs") ?: "No specifications available."

            if (productId.isEmpty()) {
                Toast.makeText(this, "Error: Product ID is missing!", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // ── Populate UI ──────────────────────────────────────
            findViewById<TextView>(R.id.tvProductName).text  = productName
            findViewById<TextView>(R.id.tvProductDesc).text  = productDesc

            // Price: ensure ₹ prefix, show strikethrough original (+20% implied)
            val cleanPrice = "₹${productPrice.replace("₹", "").trim()}"
            val tvPrice = findViewById<TextView>(R.id.tvProductPrice)
            tvPrice.text = cleanPrice

            // Show a fake original price with strikethrough for visual appeal
            val tvOriginal = findViewById<TextView>(R.id.tvProductOriginalPrice)
            val numericPrice = productPrice.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            if (numericPrice != null && numericPrice > 0) {
                val originalPrice = (numericPrice * 1.2).toInt()
                tvOriginal.text = "₹$originalPrice"
                tvOriginal.paintFlags = tvOriginal.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvOriginal.visibility = android.view.View.VISIBLE

                // Show discount badge
                val discountPct = (((originalPrice - numericPrice) / originalPrice) * 100).toInt()
                val tvDiscount = findViewById<TextView>(R.id.tvDiscount)
                tvDiscount.text = "-${discountPct}%"
                tvDiscount.visibility = android.view.View.VISIBLE
            } else {
                tvOriginal.visibility = android.view.View.GONE
            }

            // Specs
            val tvSpecs = findViewById<TextView>(R.id.tvProductSpecs)
            if (productSpecs.isNotBlank() && productSpecs != "No specifications available.") {
                tvSpecs.text = productSpecs
            }

            // Product image
            val ivProductImage = findViewById<ImageView>(R.id.ivProductImage)
            if (productImage.isNotEmpty()) {
                Glide.with(this)
                    .load(productImage)
                    .placeholder(R.drawable.shoesgreen3)
                    .centerInside()
                    .into(ivProductImage)
            }

            // ── Wishlist Heart ───────────────────────────────────
            ivHeart = findViewById(R.id.ivWishlistHeart)
            checkIfWishlisted(productId)

            ivHeart.setOnClickListener {
                if (uid == null) {
                    Toast.makeText(this, "Please log in to use Wishlist", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isWishlisted) removeFromWishlist(productId)
                else addToWishlist(productId, productName, productPrice, productImage)
            }

            // ── Back button ──────────────────────────────────────
            findViewById<ImageView>(R.id.ivBackProduct).setOnClickListener { finish() }

            // ── Size selector ────────────────────────────────────
            setupSizeSelector()

            // ── Add to Cart ──────────────────────────────────────
            findViewById<Button>(R.id.btnAddToCart).setOnClickListener {
                if (uid == null) {
                    Toast.makeText(this, "Please log in to add to cart", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                addToCart(productId, productName, productPrice, productImage)
            }

            // ── Buy Now ──────────────────────────────────────────
            findViewById<Button>(R.id.btnBuyNow).setOnClickListener {
                if (uid == null) {
                    Toast.makeText(this, "Please log in to buy items", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                addToCart(productId, productName, productPrice, productImage, navigateToCart = true)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            finish()
        }
    }

    // ─── Size Selector Logic ─────────────────────────────────────────────────
    private fun setupSizeSelector() {
        sizeButtonIds.forEach { (id, size) ->
            val btn = findViewById<TextView>(id)
            btn.setOnClickListener {
                selectedSize = size
                updateSizeChips()
                Toast.makeText(this, "Size $size selected", Toast.LENGTH_SHORT).show()
            }
        }
        updateSizeChips()
    }

    private fun updateSizeChips() {
        sizeButtonIds.forEach { (id, size) ->
            val btn = findViewById<TextView>(id)
            if (size == selectedSize) {
                btn.setBackgroundResource(R.drawable.bg_size_chip_selected)
                btn.setTextColor(android.graphics.Color.WHITE)
                btn.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                btn.setBackgroundResource(R.drawable.bg_size_chip_unselected)
                btn.setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
                btn.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    // ─── Wishlist ────────────────────────────────────────────────────────────
    private fun checkIfWishlisted(productId: String) {
        val uid = uid ?: return
        db.collection("wishlists").document(uid)
            .collection("items").document(productId).get()
            .addOnSuccessListener { doc ->
                isWishlisted = doc.exists()
                updateHeartIcon()
            }
    }

    private fun addToWishlist(id: String, name: String, price: String, imageUrl: String) {
        val uid = uid ?: return
        val item = hashMapOf("id" to id, "name" to name, "price" to price, "imageUrl" to imageUrl)
        db.collection("wishlists").document(uid).collection("items").document(id).set(item)
            .addOnSuccessListener {
                isWishlisted = true
                updateHeartIcon()
                Toast.makeText(this, "❤️ Added to Wishlist!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeFromWishlist(productId: String) {
        val uid = uid ?: return
        db.collection("wishlists").document(uid).collection("items").document(productId).delete()
            .addOnSuccessListener {
                isWishlisted = false
                updateHeartIcon()
                Toast.makeText(this, "Removed from Wishlist", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateHeartIcon() {
        if (isWishlisted) {
            ivHeart.setImageResource(R.drawable.ic_wishlist)
            ivHeart.setColorFilter(android.graphics.Color.parseColor("#E53935"))
        } else {
            ivHeart.setImageResource(R.drawable.ic_wishlist)
            ivHeart.setColorFilter(android.graphics.Color.parseColor("#CCCCCC"))
        }
    }

    // ─── Cart ────────────────────────────────────────────────────────────────
    private fun addToCart(id: String, name: String, price: String, imageUrl: String, navigateToCart: Boolean = false) {
        val uid = uid ?: return
        val cartRef = db.collection("carts").document(uid).collection("items").document(id)

        cartRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val currentQty = doc.getLong("quantity") ?: 1L
                cartRef.update("quantity", currentQty + 1).addOnSuccessListener {
                    Toast.makeText(this, "🛒 Cart updated! (Size: $selectedSize)", Toast.LENGTH_SHORT).show()
                    if (navigateToCart) startActivity(Intent(this, CartActivity::class.java))
                }
            } else {
                val item = hashMapOf(
                    "id"       to id,
                    "name"     to name,
                    "price"    to price,
                    "imageUrl" to imageUrl,
                    "quantity" to 1,
                    "size"     to selectedSize
                )
                cartRef.set(item).addOnSuccessListener {
                    Toast.makeText(this, "✅ Added to Cart! (Size: $selectedSize)", Toast.LENGTH_SHORT).show()
                    if (navigateToCart) startActivity(Intent(this, CartActivity::class.java))
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to add to cart", Toast.LENGTH_SHORT).show()
        }
    }
}
