package com.example.androidhack

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
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

            // Multi-image list: prefer imageUrls, fall back to single productImage
            val imageUrls: ArrayList<String> =
                intent.getStringArrayListExtra("productImageUrls")
                    ?: if (productImage.isNotEmpty()) arrayListOf(productImage) else arrayListOf()

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
                tvOriginal.visibility = View.VISIBLE

                // Show discount badge
                val discountPct = (((originalPrice - numericPrice) / originalPrice) * 100).toInt()
                val tvDiscount = findViewById<TextView>(R.id.tvDiscount)
                tvDiscount.text = "-${discountPct}%"
                tvDiscount.visibility = View.VISIBLE
            } else {
                tvOriginal.visibility = View.GONE
            }

            // Specs
            val tvSpecs = findViewById<TextView>(R.id.tvProductSpecs)
            if (productSpecs.isNotBlank() && productSpecs != "No specifications available.") {
                tvSpecs.text = productSpecs
            }

            // ── Setup image ViewPager2 ───────────────────────────
            val vpImages    = findViewById<ViewPager2>(R.id.vpProductImages)
            val llDots      = findViewById<LinearLayout>(R.id.llImageDots)

            if (imageUrls.isNotEmpty()) {
                val imageAdapter = ProductImageAdapter(imageUrls)
                vpImages.adapter = imageAdapter
                setupImageDots(llDots, imageUrls.size)

                vpImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updateImageDots(llDots, position)
                    }
                })
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
                else addToWishlist(productId, productName, productPrice, imageUrls.firstOrNull() ?: "")
            }

            // ── Back button ──────────────────────────────────────
            findViewById<ImageView>(R.id.ivBackProduct).setOnClickListener { finish() }

            // ── Size selector ────────────────────────────────────
            setupSizeSelector()

            // ── Add to Cart ──────────────────────────────────────
            val firstImage = imageUrls.firstOrNull() ?: ""
            findViewById<Button>(R.id.btnAddToCart).setOnClickListener {
                if (uid == null) {
                    Toast.makeText(this, "Please log in to add to cart", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                addToCart(productId, productName, productPrice, firstImage)
            }

            // ── Buy Now ──────────────────────────────────────────
            findViewById<Button>(R.id.btnBuyNow).setOnClickListener {
                if (uid == null) {
                    Toast.makeText(this, "Please log in to buy items", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                addToCart(productId, productName, productPrice, firstImage, navigateToCart = true)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            finish()
        }
    }

    // ─── Dot indicator helpers ───────────────────────────────────────────────
    private fun setupImageDots(container: LinearLayout, count: Int) {
        container.removeAllViews()
        if (count <= 1) { container.visibility = View.GONE; return }
        container.visibility = View.VISIBLE
        for (i in 0 until count) {
            val dot = View(this).apply {
                val size = if (i == 0) 24 else 16
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.setMargins(6, 0, 6, 0)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (i == 0) Color.parseColor("#7C4DFF") else Color.parseColor("#CCCCCC"))
                }
            }
            container.addView(dot)
        }
    }

    private fun updateImageDots(container: LinearLayout, activeIndex: Int) {
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val size = if (i == activeIndex) 24 else 16
            dot.layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.setMargins(6, 0, 6, 0)
            }
            (dot.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                if (i == activeIndex) Color.parseColor("#7C4DFF") else Color.parseColor("#CCCCCC")
            )
        }
    }

    // ─── Product Image ViewPager2 Adapter ────────────────────────────────────
    inner class ProductImageAdapter(private val urls: List<String>) :
        RecyclerView.Adapter<ProductImageAdapter.ImageVH>() {

        inner class ImageVH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view.findViewById(R.id.ivBannerSlide)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_product_image, parent, false)
            return ImageVH(view)
        }

        override fun getItemCount() = urls.size

        override fun onBindViewHolder(holder: ImageVH, position: Int) {
            holder.iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(holder.iv.context)
                .load(urls[position].optimizeCloudinaryUrl())
                .placeholder(R.drawable.shoesgreen3)
                .error(R.drawable.shoesgreen3)
                .centerCrop()
                .into(holder.iv)
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
