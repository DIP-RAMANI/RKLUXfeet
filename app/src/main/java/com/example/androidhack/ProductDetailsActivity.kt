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

    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var isVideoMuted = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_product_details)

            exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
            exoPlayer?.volume = 0f
            exoPlayer?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE

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

            // Save to recently viewed
            val prefs = getSharedPreferences("recently_viewed", android.content.Context.MODE_PRIVATE)
            val recentStr = prefs.getString("ids", "") ?: ""
            val recentList = recentStr.split(",").filter { it.isNotEmpty() }.toMutableList()
            recentList.remove(productId)
            recentList.add(0, productId)
            if (recentList.size > 7) {
                recentList.removeLast()
            }
            prefs.edit().putString("ids", recentList.joinToString(",")).apply()

            // ── Populate UI ──────────────────────────────────────
            findViewById<TextView>(R.id.tvProductName).text  = productName
            findViewById<TextView>(R.id.tvProductDesc).text  = productDesc

            // Fetch real rating from Firestore
            db.collection("products").document(productId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val avgRating = doc.getDouble("avgRating") ?: 0.0
                    val reviewCount = doc.getLong("reviewCount") ?: 0L
                    val tvRating = findViewById<TextView>(R.id.tvProductRating)
                    if (reviewCount > 0) {
                        tvRating.text = String.format("%.1f (%d)", avgRating, reviewCount)
                    } else {
                        tvRating.text = "New"
                    }
                }
            }

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

            // ── Product Story ───────────────────────────────
            val productStory = intent.getStringExtra("productStory") ?: ""
            val llProductStory = findViewById<LinearLayout>(R.id.llProductStory)
            val tvProductStory = findViewById<TextView>(R.id.tvProductStory)
            if (productStory.isNotBlank()) {
                llProductStory.visibility = View.VISIBLE
                tvProductStory.text = productStory
            }

            // ── Features & Benefits ─────────────────────────
            val productFeatures = intent.getStringExtra("productFeatures") ?: ""
            val llFeaturesDetails = findViewById<LinearLayout>(R.id.llFeaturesDetails)
            val llFeatures = findViewById<LinearLayout>(R.id.llFeatures)
            val tvProductFeatures = findViewById<TextView>(R.id.tvProductFeatures)
            if (productFeatures.isNotBlank()) {
                llFeaturesDetails.visibility = View.VISIBLE
                llFeatures.visibility = View.VISIBLE
                // Add bullet points to each line
                val bulletFeatures = productFeatures.lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "•  $it" }
                tvProductFeatures.text = bulletFeatures
            }

            // ── Details ─────────────────────────────────────
            val productDetails = intent.getStringExtra("productDetails") ?: ""
            val llDetails = findViewById<LinearLayout>(R.id.llDetails)
            val tvProductDetailsView = findViewById<TextView>(R.id.tvProductDetails)
            if (productDetails.isNotBlank()) {
                llFeaturesDetails.visibility = View.VISIBLE
                llDetails.visibility = View.VISIBLE
                // Add bullet points to each line
                val bulletDetails = productDetails.lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "•  $it" }
                tvProductDetailsView.text = bulletDetails
            }

            // Hide legacy specs section if new fields are present
            val llSpecs = findViewById<LinearLayout>(R.id.llSpecs)
            val tvProductDescView = findViewById<TextView>(R.id.tvProductDesc)
            
            if (productStory.isNotBlank() || productFeatures.isNotBlank() || productDetails.isNotBlank()) {
                llSpecs.visibility = View.GONE
            }
            
            // Fix: Hide legacy description if it's the exact same as product story to prevent showing TWO TIMES
            if (productStory.isNotBlank() && (productStory.trim() == productDesc.trim() || productDesc.isBlank() || productDesc == "No description available.")) {
                tvProductDescView.visibility = View.GONE
            }

            // ── Setup image ViewPager2 ───────────────────────────
            val vpImages    = findViewById<ViewPager2>(R.id.vpProductImages)
            val llDots      = findViewById<LinearLayout>(R.id.llImageDots)

            if (imageUrls.isNotEmpty()) {
                val imageAdapter = ProductImageAdapter(imageUrls)
                vpImages.adapter = imageAdapter
                setupImageDots(llDots, imageUrls.size, imageUrls)

                vpImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updateImageDots(llDots, position, imageUrls)
                        
                        // Autoplay video if current slide is video, else pause
                        val url = imageUrls.getOrNull(position) ?: ""
                        val isVideo = url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".mkv") || url.endsWith(".webm")
                        if (isVideo) {
                            exoPlayer?.play()
                        } else {
                            exoPlayer?.pause()
                        }
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

            // ── Size Guide & Find My Size ─────────────────────────
            findViewById<View>(R.id.btnSizeGuide).setOnClickListener { showSizeChart() }
            findViewById<View>(R.id.btnFindMySize).setOnClickListener { showSizeFinder() }

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

            // ── Reviews ─────────────────────────────────────────
            findViewById<TextView>(R.id.btnWriteReview).setOnClickListener {
                if (uid == null) {
                    Toast.makeText(this, "Please log in to write a review", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Check if user has purchased the product
                db.collection("orders")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        var hasPurchased = false
                        for (doc in snapshot.documents) {
                            val items = doc.get("items") as? List<Map<String, Any>> ?: continue
                            for (item in items) {
                                if (item["id"] == productId) {
                                    hasPurchased = true
                                    break
                                }
                            }
                            if (hasPurchased) break
                        }

                        if (hasPurchased) {
                            showWriteReviewDialog(productId)
                        } else {
                            Toast.makeText(this@ProductDetailsActivity, "You can only review products you have purchased.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@ProductDetailsActivity, "Failed to verify purchase history.", Toast.LENGTH_SHORT).show()
                    }
            }
            loadReviews(productId)

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            finish()
        }
    }

    // ─── Dot indicator helpers ───────────────────────────────────────────────
    private fun setupImageDots(container: LinearLayout, count: Int, imageUrls: List<String>) {
        container.removeAllViews()
        if (count <= 1) { container.visibility = View.GONE; return }
        container.visibility = View.VISIBLE
        for (i in 0 until count) {
            val url = imageUrls.getOrNull(i) ?: ""
            val isVideo = url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".mkv") || url.endsWith(".webm")

            if (isVideo) {
                val dot = TextView(this).apply {
                    text = "▶"
                    textSize = 8f
                    setTextColor(Color.WHITE)
                    gravity = android.view.Gravity.CENTER
                    // Convert 24dp/16dp to pixels for better sizing, or just use 24/16 if it was originally pixels
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
            } else {
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
    }

    private fun updateImageDots(container: LinearLayout, activeIndex: Int, imageUrls: List<String>) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val url = imageUrls.getOrNull(i) ?: ""
            val isVideo = url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".mkv") || url.endsWith(".webm")

            val size = if (i == activeIndex) 24 else 16
            child.layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.setMargins(6, 0, 6, 0)
            }
            (child.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                if (i == activeIndex) Color.parseColor("#7C4DFF") else Color.parseColor("#CCCCCC")
            )

            if (isVideo && child is TextView) {
                child.textSize = if (i == activeIndex) 10f else 8f
            }
        }
    }

    // ─── Product Image ViewPager2 Adapter ────────────────────────────────────
    inner class ProductImageAdapter(private val urls: List<String>) :
        RecyclerView.Adapter<ProductImageAdapter.MediaVH>() {

        inner class MediaVH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view.findViewById(R.id.ivBannerSlide)
            val pvVideo: androidx.media3.ui.PlayerView = view.findViewById(R.id.pvVideoSlide)
            val tvSpeaker: TextView = view.findViewById(R.id.tvSpeakerToggle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_product_image, parent, false)
            return MediaVH(view)
        }

        override fun getItemCount() = urls.size

        override fun onBindViewHolder(holder: MediaVH, position: Int) {
            val url = urls[position]
            val isVideo = url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".mkv") || url.endsWith(".webm")

            if (isVideo) {
                holder.iv.visibility = View.GONE
                holder.pvVideo.visibility = View.VISIBLE
                holder.tvSpeaker.visibility = View.VISIBLE
                
                holder.pvVideo.player = exoPlayer
                val mediaItem = androidx.media3.common.MediaItem.fromUri(url)
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
                
                holder.tvSpeaker.text = if (isVideoMuted) "🔇" else "🔊"
                holder.tvSpeaker.setOnClickListener {
                    isVideoMuted = !isVideoMuted
                    exoPlayer?.volume = if (isVideoMuted) 0f else 1f
                    holder.tvSpeaker.text = if (isVideoMuted) "🔇" else "🔊"
                }
            } else {
                holder.pvVideo.visibility = View.GONE
                holder.tvSpeaker.visibility = View.GONE
                holder.iv.visibility = View.VISIBLE
                holder.pvVideo.player = null

                holder.iv.scaleType = ImageView.ScaleType.FIT_CENTER
                Glide.with(holder.iv.context)
                    .load(url.optimizeCloudinaryUrl())
                    .placeholder(R.drawable.shoesgreen3)
                    .error(R.drawable.shoesgreen3)
                    .fitCenter()
                    .into(holder.iv)
            }
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

        // Use a compound key: productId + size so each size is a SEPARATE cart item
        val cartItemKey = "${id}_${selectedSize}"
        val cartRef = db.collection("carts").document(uid).collection("items").document(cartItemKey)

        cartRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                // Same product + same size → just increase quantity
                val currentQty = doc.getLong("quantity") ?: 1L
                cartRef.update("quantity", currentQty + 1).addOnSuccessListener {
                    Toast.makeText(this, "🛒 Cart updated! (Size: $selectedSize)", Toast.LENGTH_SHORT).show()
                    if (navigateToCart) startActivity(Intent(this, CartActivity::class.java))
                }
            } else {
                // New product or new size → create a fresh cart item
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

    // ─── Size Chart Bottom Sheet ─────────────────────────────────────────────
    private fun showSizeChart() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_size_chart, null)
        dialog.setContentView(view)

        // Size data: brand → list of (UK, US, EU, CM)
        val sizeData = mapOf(
            "nike" to listOf(
                arrayOf("6", "7", "39", "24.5"),
                arrayOf("7", "8", "40", "25.0"),
                arrayOf("8", "9", "41", "26.0"),
                arrayOf("9", "10", "42.5", "27.0"),
                arrayOf("10", "11", "44", "28.0")
            ),
            "adidas" to listOf(
                arrayOf("6", "6.5", "39.3", "24.0"),
                arrayOf("7", "7.5", "40.7", "25.0"),
                arrayOf("8", "8.5", "42", "26.0"),
                arrayOf("9", "9.5", "43.3", "27.0"),
                arrayOf("10", "10.5", "44.7", "28.0")
            ),
            "puma" to listOf(
                arrayOf("6", "7", "39", "24.5"),
                arrayOf("7", "8", "40.5", "25.5"),
                arrayOf("8", "9", "42", "26.5"),
                arrayOf("9", "10", "43", "27.5"),
                arrayOf("10", "11", "44.5", "28.5")
            ),
            "asics" to listOf(
                arrayOf("6", "7", "39.5", "24.5"),
                arrayOf("7", "8", "40.5", "25.0"),
                arrayOf("8", "9", "42", "26.0"),
                arrayOf("9", "10", "43.5", "27.0"),
                arrayOf("10", "11", "44.5", "28.0")
            ),
            "generic" to listOf(
                arrayOf("6", "7", "39", "24.5"),
                arrayOf("7", "8", "40.5", "25.0"),
                arrayOf("8", "9", "42", "26.0"),
                arrayOf("9", "10", "43", "27.0"),
                arrayOf("10", "11", "44", "28.0")
            )
        )

        var currentBrand = "nike"
        var showInches = false
        val tableBody = view.findViewById<LinearLayout>(R.id.llSizeTableBody)
        val tvLengthHeader = view.findViewById<TextView>(R.id.tvLengthHeader)
        val tvUnitToggle = view.findViewById<TextView>(R.id.tvUnitToggle)

        fun cmToInches(cm: String): String {
            val v = cm.toDoubleOrNull() ?: return cm
            return String.format("%.1f", v / 2.54)
        }

        fun populateTable() {
            tableBody.removeAllViews()
            val rows = sizeData[currentBrand] ?: return
            tvLengthHeader.text = if (showInches) "IN" else "CM"
            tvUnitToggle.text = if (showInches) "INCHES" else "CM"

            rows.forEachIndexed { i, row ->
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 44.dpToPx()
                    )
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
                    if (i % 2 == 0) setBackgroundColor(android.graphics.Color.parseColor("#FAFAFA"))
                }
                val cols = listOf(row[0], row[1], row[2], if (showInches) cmToInches(row[3]) else row[3])
                cols.forEach { text ->
                    val tv = TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        this.text = text
                        textSize = 13f
                        setTextColor(android.graphics.Color.parseColor("#424242"))
                        gravity = android.view.Gravity.CENTER
                    }
                    rowLayout.addView(tv)
                }
                tableBody.addView(rowLayout)
            }
        }

        // Brand tab click handling
        val brandTabs = listOf(
            view.findViewById<TextView>(R.id.tabNike) to "nike",
            view.findViewById<TextView>(R.id.tabAdidas) to "adidas",
            view.findViewById<TextView>(R.id.tabPuma) to "puma",
            view.findViewById<TextView>(R.id.tabAsics) to "asics",
            view.findViewById<TextView>(R.id.tabGeneric) to "generic"
        )

        fun updateTabs() {
            brandTabs.forEach { (tab, brand) ->
                if (brand == currentBrand) {
                    tab.setBackgroundResource(R.drawable.bg_brand_tab_active)
                    tab.setTextColor(android.graphics.Color.WHITE)
                    tab.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    tab.setBackgroundResource(R.drawable.bg_brand_tab_inactive)
                    tab.setTextColor(android.graphics.Color.parseColor("#757575"))
                    tab.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
        }

        brandTabs.forEach { (tab, brand) ->
            tab.setOnClickListener {
                currentBrand = brand
                updateTabs()
                populateTable()
            }
        }

        tvUnitToggle.setOnClickListener {
            showInches = !showInches
            populateTable()
        }

        updateTabs()
        populateTable()
        dialog.show()
    }

    // ─── Size Finder Quiz Bottom Sheet ───────────────────────────────────────
    private fun showSizeFinder() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_size_finder, null)
        dialog.setContentView(view)

        var step = 1
        var quizBrand = ""
        var quizSize = ""
        var quizFit = ""

        val tvQuestion = view.findViewById<TextView>(R.id.tvStepQuestion)
        val llOptions = view.findViewById<LinearLayout>(R.id.llFinderOptions)
        val llResult = view.findViewById<LinearLayout>(R.id.llFinderResult)
        val dot1 = view.findViewById<View>(R.id.dot1)
        val dot2 = view.findViewById<View>(R.id.dot2)
        val dot3 = view.findViewById<View>(R.id.dot3)

        fun updateDots() {
            dot1.setBackgroundColor(android.graphics.Color.parseColor(if (step >= 1) "#7C4DFF" else "#E0E0E0"))
            dot2.setBackgroundColor(android.graphics.Color.parseColor(if (step >= 2) "#7C4DFF" else "#E0E0E0"))
            dot3.setBackgroundColor(android.graphics.Color.parseColor(if (step >= 3) "#7C4DFF" else "#E0E0E0"))
        }

        fun addOptionChip(text: String, onClick: () -> Unit) {
            val chip = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 48.dpToPx()
                ).also { it.bottomMargin = 8.dpToPx() }
                this.text = text
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#424242"))
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(20.dpToPx(), 0, 20.dpToPx(), 0)
                setBackgroundResource(R.drawable.bg_cat_chip_unselected)
                setOnClickListener { onClick() }
            }
            llOptions.addView(chip)
        }

        fun showResult() {
            llOptions.visibility = View.GONE
            llResult.visibility = View.VISIBLE
            tvQuestion.visibility = View.GONE
            view.findViewById<View>(R.id.llStepDots).visibility = View.GONE

            // Calculate recommended size
            val sizeNum = quizSize.replace("UK ", "").toIntOrNull() ?: 7
            val recommended = when (quizFit) {
                "Tight" -> "UK ${sizeNum + 1}"
                "Loose" -> "UK ${if (sizeNum > 6) sizeNum - 1 else sizeNum}"
                else -> "UK $sizeNum"  // Perfect fit
            }

            view.findViewById<TextView>(R.id.tvResultSize).text = recommended
            view.findViewById<TextView>(R.id.tvResultDesc).text =
                "Based on your $quizBrand $quizSize with a ${quizFit.lowercase()} fit"

            view.findViewById<TextView>(R.id.btnSelectSize).setOnClickListener {
                // Auto-select the recommended size
                selectedSize = recommended
                updateSizeChips()
                Toast.makeText(this, "✅ Size $recommended selected!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        fun showStep() {
            llOptions.removeAllViews()
            updateDots()

            when (step) {
                1 -> {
                    tvQuestion.text = "What brand are you currently wearing?"
                    listOf("Nike", "Adidas", "Puma", "Asics", "Other").forEach { brand ->
                        addOptionChip("👟  $brand") {
                            quizBrand = brand
                            step = 2
                            showStep()
                        }
                    }
                }
                2 -> {
                    tvQuestion.text = "What's your current size in $quizBrand?"
                    listOf("UK 5", "UK 6", "UK 7", "UK 8", "UK 9", "UK 10", "UK 11").forEach { size ->
                        addOptionChip("📐  $size") {
                            quizSize = size
                            step = 3
                            showStep()
                        }
                    }
                }
                3 -> {
                    tvQuestion.text = "How does your current $quizBrand $quizSize fit?"
                    listOf(
                        "Tight" to "😣  Tight — feels too small",
                        "Perfect" to "👌  Perfect — just right",
                        "Loose" to "😌  Loose — feels too big"
                    ).forEach { (fit, label) ->
                        addOptionChip(label) {
                            quizFit = fit
                            showResult()
                        }
                    }
                }
            }
        }

        showStep()
        dialog.show()
    }

    // Helper: dp to px
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    // ═══════════════════════════════════════════════════════════════════════════
    // REVIEWS SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════

    data class Review(
        val id: String = "",
        val userId: String = "",
        val userName: String = "",
        val rating: Int = 5,
        val comment: String = "",
        val createdAt: com.google.firebase.Timestamp? = null,
        val profileImageUrl: String = ""
    )

    private var reviewsList = mutableListOf<Review>()
    private var reviewAdapter: ReviewAdapter? = null
    private var reviewsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun loadReviews(productId: String) {
        val rvReviews = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvReviews)
        val tvNoReviews = findViewById<TextView>(R.id.tvNoReviews)
        val tvReviewAvg = findViewById<TextView>(R.id.tvReviewAvgRating)
        val rbAvg = findViewById<android.widget.RatingBar>(R.id.rbAvgRating)
        val tvReviewCount = findViewById<TextView>(R.id.tvReviewCount)

        // Check admin role from Firestore
        val currentUid = uid
        val checkAdminAndSetup = { isAdmin: Boolean ->
            rvReviews.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            reviewAdapter = ReviewAdapter(reviewsList, isAdmin) { review ->
                deleteReview(productId, review)
            }
            rvReviews.adapter = reviewAdapter

            // Real-time listener for reviews
            reviewsListener = db.collection("products").document(productId)
                .collection("reviews")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    reviewsList.clear()
                    for (doc in snapshot.documents) {
                        reviewsList.add(
                            Review(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                userName = doc.getString("userName") ?: "Anonymous",
                                rating = (doc.getLong("rating") ?: 5).toInt(),
                                comment = doc.getString("comment") ?: "",
                                createdAt = doc.getTimestamp("createdAt"),
                                profileImageUrl = doc.getString("profileImageUrl") ?: ""
                            )
                        )
                    }
                    reviewAdapter?.notifyDataSetChanged()

                    // Update summary
                    if (reviewsList.isNotEmpty()) {
                        val avg = reviewsList.map { it.rating }.average()
                        tvReviewAvg.text = String.format("%.1f", avg)
                        rbAvg.rating = avg.toFloat()
                        tvReviewCount.text = "(${reviewsList.size} reviews)"
                        tvNoReviews.visibility = View.GONE
                    } else {
                        tvReviewAvg.text = "0.0"
                        rbAvg.rating = 0f
                        tvReviewCount.text = "(0 reviews)"
                        tvNoReviews.visibility = View.VISIBLE
                    }
                }
        }

        if (currentUid != null) {
            db.collection("users").document(currentUid).get().addOnSuccessListener { doc ->
                val isAdmin = doc.getString("role") == "admin"
                checkAdminAndSetup(isAdmin)
            }.addOnFailureListener {
                checkAdminAndSetup(false)
            }
        } else {
            checkAdminAndSetup(false)
        }
    }

    private fun showWriteReviewDialog(productId: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_write_review, null)
        dialog.setContentView(view)

        val ratingBar = view.findViewById<android.widget.RatingBar>(R.id.rbReviewRating)
        val etComment = view.findViewById<android.widget.EditText>(R.id.etReviewComment)
        val btnSubmit = view.findViewById<TextView>(R.id.btnSubmitReview)

        // Check if user already reviewed
        val currentUid = uid ?: return
        db.collection("products").document(productId)
            .collection("reviews")
            .whereEqualTo("userId", currentUid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    // Pre-fill with existing review
                    val existing = snapshot.documents.first()
                    ratingBar.rating = (existing.getLong("rating") ?: 5).toFloat()
                    etComment.setText(existing.getString("comment") ?: "")
                    btnSubmit.text = "Update Review"
                }
            }

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating.toInt()
            val comment = etComment.text.toString().trim()

            if (rating < 1) {
                Toast.makeText(this, "Please select a star rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (comment.isEmpty()) {
                Toast.makeText(this, "Please write a comment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitReview(productId, rating, comment, dialog)
        }

        dialog.show()
    }

    private fun submitReview(productId: String, rating: Int, comment: String, dialog: com.google.android.material.bottomsheet.BottomSheetDialog) {
        val currentUid = uid ?: return
        val user = FirebaseAuth.getInstance().currentUser

        // Get user name from profile
        db.collection("users").document(currentUid).get().addOnSuccessListener { userDoc ->
            val userName = userDoc.getString("name") ?: user?.displayName ?: "Anonymous"
            val profileImg = userDoc.getString("profileImageUrl") ?: user?.photoUrl?.toString() ?: ""

            val reviewData = hashMapOf(
                "userId" to currentUid,
                "userName" to userName,
                "rating" to rating,
                "comment" to comment,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "profileImageUrl" to profileImg
            )

            // Check for existing review to update instead of duplicate
            db.collection("products").document(productId)
                .collection("reviews")
                .whereEqualTo("userId", currentUid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val reviewsRef = db.collection("products").document(productId).collection("reviews")
                    
                    if (snapshot.isEmpty) {
                        // New review
                        reviewsRef.add(reviewData).addOnSuccessListener {
                            Toast.makeText(this, "⭐ Review submitted!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            recalculateRating(productId)
                        }
                    } else {
                        // Update existing
                        val existingId = snapshot.documents.first().id
                        reviewsRef.document(existingId).set(reviewData).addOnSuccessListener {
                            Toast.makeText(this, "⭐ Review updated!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            recalculateRating(productId)
                        }
                    }
                }
        }
    }

    private fun deleteReview(productId: String, review: Review) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Review")
            .setMessage("Are you sure you want to delete this review by ${review.userName}?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("products").document(productId)
                    .collection("reviews").document(review.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Review deleted", Toast.LENGTH_SHORT).show()
                        recalculateRating(productId)
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun recalculateRating(productId: String) {
        db.collection("products").document(productId)
            .collection("reviews")
            .get()
            .addOnSuccessListener { snapshot ->
                val reviews = snapshot.documents
                val count = reviews.size
                val avg = if (count > 0) reviews.mapNotNull { it.getLong("rating")?.toDouble() }.average() else 0.0

                db.collection("products").document(productId).update(
                    "avgRating", avg,
                    "reviewCount", count.toLong()
                )
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        reviewsListener?.remove()
        exoPlayer?.release()
    }

    // ── Review Adapter ──────────────────────────────────────────────────────
    class ReviewAdapter(
        private val reviews: List<Review>,
        private val isAdmin: Boolean,
        private val onDelete: (Review) -> Unit
    ) : RecyclerView.Adapter<ReviewAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivReviewAvatar)
            val tvName: TextView = view.findViewById(R.id.tvReviewUserName)
            val tvDate: TextView = view.findViewById(R.id.tvReviewDate)
            val rbStars: android.widget.RatingBar = view.findViewById(R.id.rbReviewStars)
            val tvComment: TextView = view.findViewById(R.id.tvReviewComment)
            val ivDelete: ImageView = view.findViewById(R.id.ivDeleteReview)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false))

        override fun getItemCount() = reviews.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val review = reviews[position]
            holder.tvName.text = review.userName
            holder.rbStars.rating = review.rating.toFloat()
            holder.tvComment.text = review.comment

            // Format date
            if (review.createdAt != null) {
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                holder.tvDate.text = sdf.format(review.createdAt.toDate())
            } else {
                holder.tvDate.text = ""
            }

            // Avatar
            if (review.profileImageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(review.profileImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(holder.ivAvatar)
            }

            // Admin delete button
            if (isAdmin) {
                holder.ivDelete.visibility = View.VISIBLE
                holder.ivDelete.setOnClickListener { onDelete(review) }
            } else {
                holder.ivDelete.visibility = View.GONE
            }
        }
    }
}
