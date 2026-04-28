package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class StoreActivity : AppCompatActivity() {

    data class Shoe(
        val id: String = "",
        val name: String = "",
        val price: String = "",
        val imageUrl: String = "",
        val imageUrls: List<String> = emptyList(),
        val brand: String = "",
        val shoeType: String = "",
        val timestamp: Long = 0L,
        val popularity: Int = 0,
        val discountPercent: Int = 0,
        val description: String = "",
        val specifications: String = "",
        val productStory: String = "",
        val features: String = "",
        val details: String = "",
        val avgRating: Double = 0.0,
        val reviewCount: Int = 0
    )

    private lateinit var adapter: GridShoeAdapter
    private var allShoes = listOf<Shoe>()
    private var currentSort = "all"
    private var currentBrand = "all"
    private var currentType = "all"
    private var currentSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        val rvStoreGrid = findViewById<RecyclerView>(R.id.rvStoreGrid)
        val bottomNav = findViewById<com.ismaeldivita.chipnavigation.ChipNavigationBar>(R.id.bottomNavigation)
        val tvCount = findViewById<TextView>(R.id.tvResultsCount)

        findViewById<ImageView>(R.id.ivBackStore).setOnClickListener { finish() }
        findViewById<androidx.cardview.widget.CardView>(R.id.ivCart)?.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        adapter = GridShoeAdapter(emptyList()) { shoe ->
            val intent = Intent(this, ProductDetailsActivity::class.java).apply {
                putExtra("productId",     shoe.id)
                putExtra("productName",   shoe.name)
                putExtra("productPrice",  shoe.price)
                putExtra("productImage",  shoe.imageUrl)
                putStringArrayListExtra("productImageUrls", ArrayList(shoe.imageUrls.ifEmpty { listOf(shoe.imageUrl) }))
                putExtra("productDesc",   shoe.description)
                putExtra("productSpecs",  shoe.specifications)
                putExtra("productStory",    shoe.productStory)
                putExtra("productFeatures", shoe.features)
                putExtra("productDetails",  shoe.details)
            }
            startActivity(intent)
        }
        rvStoreGrid.adapter = adapter

        // Load products from Firestore
        FirebaseFirestore.getInstance().collection("products")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    allShoes = snapshot.documents.map { doc ->
                        val urls = (doc.get("imageUrls") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val primary = doc.getString("imageUrl") ?: ""
                        // Pick first non-video URL as thumbnail
                        val thumbnailUrl = urls.firstOrNull { !it.endsWith(".mp4") && !it.endsWith(".mov") && !it.endsWith(".webm") }
                            ?: if (!primary.endsWith(".mp4") && !primary.endsWith(".mov") && !primary.endsWith(".webm")) primary else ""
                        Shoe(
                            id              = doc.id,
                            name            = doc.getString("name") ?: "",
                            price           = doc.getString("price") ?: "",
                            imageUrl        = thumbnailUrl,
                            imageUrls       = urls.ifEmpty { if (primary.isNotEmpty()) listOf(primary) else emptyList() },
                            brand           = doc.getString("brand") ?: "",
                            shoeType        = doc.getString("shoeType") ?: "",
                            timestamp       = doc.getTimestamp("createdAt")?.toDate()?.time ?: doc.getLong("timestamp") ?: 0L,
                            popularity      = doc.getLong("popularity")?.toInt() ?: 0,
                            discountPercent = doc.getLong("discountPercent")?.toInt() ?: 0,
                            description     = doc.getString("description") ?: "",
                            specifications  = doc.getString("specifications") ?: "",
                            productStory    = doc.getString("productStory") ?: "",
                            features        = doc.getString("features") ?: "",
                            details         = doc.getString("details") ?: "",
                            avgRating       = doc.getDouble("avgRating") ?: 0.0,
                            reviewCount     = doc.getLong("reviewCount")?.toInt() ?: 0
                        )
                    }
                    if (allShoes.isEmpty()) {
                        // Fallback sample data
                        allShoes = listOf(
                            Shoe("1", "Nike Air Max 2025", "₹2999", "https://i.imgur.com/yMu7M8t.png"),
                            Shoe("2", "Adidas Ultra Boost", "₹3499", "https://i.imgur.com/yMu7M8t.png"),
                            Shoe("3", "Puma Sport Runner", "₹1999", "https://i.imgur.com/yMu7M8t.png"),
                            Shoe("4", "Asics Gel Nimbus", "₹4999", "https://i.imgur.com/yMu7M8t.png")
                        )
                    }
                    applyFiltersAndSort()
                }
            }

        // Live Search
        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearch = s.toString().trim()
                applyFiltersAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter button opens bottom sheet
        findViewById<androidx.cardview.widget.CardView>(R.id.cvFilterBtn).setOnClickListener {
            showFilterBottomSheet()
        }

        // Handle initial filters from intent
        val initialSort = intent.getStringExtra("initial_sort") ?: "all"
        if (initialSort != "all") currentSort = initialSort
        val initialType = intent.getStringExtra("initial_type") ?: "all"
        if (initialType != "all") currentType = initialType

        updateActiveFilterLabel()

        // Bottom nav
        bottomNav.setItemSelected(R.id.nav_shop, true)
        bottomNav.setOnItemSelectedListener { id ->
            if (id == R.id.nav_shop) return@setOnItemSelectedListener
            val intent = when (id) {
                R.id.nav_home -> Intent(this@StoreActivity, HomeActivity::class.java)
                R.id.nav_wishlist -> Intent(this@StoreActivity, WishlistActivity::class.java)
                R.id.nav_profile -> Intent(this@StoreActivity, ProfileActivity::class.java)
                else -> null
            }
            intent?.let {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startActivity(it)
                    overridePendingTransition(0, 0)
                    finish()
                }, 150)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCartBadge()
    }

    private fun updateCartBadge() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val tvCartBadge = findViewById<android.widget.TextView>(R.id.tvCartBadge) ?: return
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("carts").document(uid).collection("items")
            .addSnapshotListener { snapshot, _ ->
                val count = snapshot?.size() ?: 0
                if (count > 0) {
                    tvCartBadge.visibility = android.view.View.VISIBLE
                    tvCartBadge.text = count.toString()
                } else {
                    tvCartBadge.visibility = android.view.View.GONE
                }
            }
    }

    private fun updateActiveFilterLabel() {
        val parts = mutableListOf<String>()
        if (currentSort != "all") {
            val sortLabel = when (currentSort) {
                "price_low" -> "Low→High"
                "price_high" -> "High→Low"
                "new" -> "New Arrivals"
                "bestseller" -> "Bestsellers"
                else -> ""
            }
            if (sortLabel.isNotEmpty()) parts.add(sortLabel)
        }
        if (currentBrand != "all") parts.add(currentBrand.replaceFirstChar { it.uppercase() })
        if (currentType != "all") parts.add(currentType.replaceFirstChar { it.uppercase() })

        val tv = findViewById<TextView>(R.id.tvActiveFilters)
        tv.text = if (parts.isNotEmpty()) parts.joinToString(" · ") else ""
    }

    private fun showFilterBottomSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_filter, null)
        dialog.setContentView(view)

        // Make background transparent so our rounded corners show
        (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // Temporary state while user picks filters
        var tempSort = currentSort
        var tempBrand = currentBrand
        var tempType = currentType

        // === SORT CHIPS ===
        val sortChips = mapOf(
            R.id.chipAll to "all",
            R.id.chipPriceLow to "price_low",
            R.id.chipPriceHigh to "price_high",
            R.id.chipNewArrivals to "new",
            R.id.chipBestseller to "bestseller"
        )
        fun updateSortUI() {
            sortChips.forEach { (id, sort) ->
                val chip = view.findViewById<TextView>(id)
                if (sort == tempSort) {
                    chip.setBackgroundResource(R.drawable.bg_filter_chip_selected)
                    chip.setTextColor(android.graphics.Color.WHITE)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_filter_chip)
                    chip.setTextColor(android.graphics.Color.parseColor("#424242"))
                }
            }
        }
        updateSortUI()
        sortChips.forEach { (id, sort) ->
            view.findViewById<TextView>(id).setOnClickListener {
                tempSort = sort
                updateSortUI()
            }
        }

        // === BRAND CHIPS ===
        val brandChips = mapOf(
            R.id.chipBrandAll to "all",
            R.id.chipBrandNike to "nike",
            R.id.chipBrandAdidas to "adidas",
            R.id.chipBrandPuma to "puma",
            R.id.chipBrandAsics to "asics",
            R.id.chipBrandOther to "other"
        )
        val brandTextIds = mapOf(
            R.id.chipBrandAll to R.id.tvChipBrandAll,
            R.id.chipBrandNike to R.id.tvChipBrandNike,
            R.id.chipBrandAdidas to R.id.tvChipBrandAdidas,
            R.id.chipBrandPuma to R.id.tvChipBrandPuma,
            R.id.chipBrandAsics to R.id.tvChipBrandAsics,
            R.id.chipBrandOther to R.id.tvChipBrandOther
        )
        fun updateBrandUI() {
            brandChips.forEach { (id, brand) ->
                val container = view.findViewById<View>(id)
                val tv = view.findViewById<TextView>(brandTextIds[id]!!)
                if (brand == tempBrand) {
                    container.setBackgroundResource(R.drawable.bg_filter_chip_selected)
                    tv.setTextColor(android.graphics.Color.WHITE)
                } else {
                    container.setBackgroundResource(R.drawable.bg_filter_chip)
                    tv.setTextColor(android.graphics.Color.parseColor("#424242"))
                }
            }
        }
        updateBrandUI()
        brandChips.forEach { (id, brand) ->
            view.findViewById<View>(id).setOnClickListener {
                tempBrand = brand
                updateBrandUI()
            }
        }

        // === TYPE CHIPS ===
        val typeChips = mapOf(
            R.id.chipTypeAll to "all",
            R.id.chipTypeRunning to "running",
            R.id.chipTypeCasual to "casual",
            R.id.chipTypeFormal to "formal",
            R.id.chipTypeSports to "sports",
            R.id.chipTypeSneakers to "sneakers",
            R.id.chipTypeLoafers to "loafers"
        )
        val typeTextIds = mapOf(
            R.id.chipTypeAll to R.id.tvChipTypeAll,
            R.id.chipTypeRunning to R.id.tvChipTypeRunning,
            R.id.chipTypeCasual to R.id.tvChipTypeCasual,
            R.id.chipTypeFormal to R.id.tvChipTypeFormal,
            R.id.chipTypeSports to R.id.tvChipTypeSports,
            R.id.chipTypeSneakers to R.id.tvChipTypeSneakers,
            R.id.chipTypeLoafers to R.id.tvChipTypeLoafers
        )
        fun updateTypeUI() {
            typeChips.forEach { (id, type) ->
                val container = view.findViewById<View>(id)
                val tv = view.findViewById<TextView>(typeTextIds[id]!!)
                if (type == tempType) {
                    container.setBackgroundResource(R.drawable.bg_filter_chip_selected)
                    tv.setTextColor(android.graphics.Color.WHITE)
                } else {
                    container.setBackgroundResource(R.drawable.bg_filter_chip)
                    tv.setTextColor(android.graphics.Color.parseColor("#424242"))
                }
            }
        }
        updateTypeUI()
        typeChips.forEach { (id, type) ->
            view.findViewById<View>(id).setOnClickListener {
                tempType = type
                updateTypeUI()
            }
        }

        // Reset All
        view.findViewById<TextView>(R.id.tvResetFilters).setOnClickListener {
            tempSort = "all"
            tempBrand = "all"
            tempType = "all"
            updateSortUI()
            updateBrandUI()
            updateTypeUI()
        }

        // Apply
        view.findViewById<android.widget.Button>(R.id.btnApplyFilters).setOnClickListener {
            currentSort = tempSort
            currentBrand = tempBrand
            currentType = tempType
            applyFiltersAndSort()
            updateActiveFilterLabel()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun parsePrice(price: String): Double {
        return price.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
    }

    private fun applyFiltersAndSort() {
        var filtered = allShoes

        // Search filter
        if (currentSearch.isNotEmpty()) {
            filtered = filtered.filter { it.name.contains(currentSearch, ignoreCase = true) }
        }

        // Brand filter — uses the real `brand` Firestore field
        if (currentBrand != "all") {
            filtered = if (currentBrand == "other") {
                filtered.filter { shoe ->
                    val b = shoe.brand.lowercase()
                    b.isEmpty() || (b != "nike" && b != "adidas" && b != "puma" && b != "asics")
                }
            } else {
                filtered.filter { it.brand.equals(currentBrand, ignoreCase = true) }
            }
        }

        // Type filter — uses the `shoeType` Firestore field
        if (currentType != "all") {
            filtered = filtered.filter { it.shoeType.equals(currentType, ignoreCase = true) }
        }

        // Sort
        filtered = when (currentSort) {
            "price_low"   -> filtered.sortedBy { parsePrice(it.price) }
            "price_high"  -> filtered.sortedByDescending { parsePrice(it.price) }
            "new"         -> filtered.sortedByDescending { it.timestamp }  // newest first
            "bestseller"  -> filtered.sortedByDescending { it.popularity }
            else          -> filtered
        }

        adapter.updateData(filtered)
        findViewById<TextView>(R.id.tvResultsCount).text = "${filtered.size} results"
    }



    class GridShoeAdapter(
        private var shoes: List<Shoe>,
        private val onClick: (Shoe) -> Unit
    ) : RecyclerView.Adapter<GridShoeAdapter.ViewHolder>() {

        fun updateData(newShoes: List<Shoe>) {
            shoes = newShoes
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView  = view.findViewById(R.id.tvGridShoeName)
            val tvPrice: TextView = view.findViewById(R.id.tvGridShoePrice)
            val ivShoe: ImageView = view.findViewById(R.id.ivGridShoe)
            val rlImageContainer: View = view.findViewById(R.id.rlGridImageContainer)
            val cvBadge: androidx.cardview.widget.CardView = view.findViewById(R.id.cvGridBadge)
            val tvBadge: TextView = view.findViewById(R.id.tvGridBadge)
            val tvRating: TextView = view.findViewById(R.id.tvGridRating)
            val tvSold: TextView = view.findViewById(R.id.tvGridSold)
            val btnAddToCart: TextView = view.findViewById(R.id.btnGridAddToCart)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_shoe_grid, parent, false))

        override fun getItemCount() = shoes.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val shoe = shoes[position]
            holder.tvName.text  = shoe.name
            holder.tvPrice.text = "₹${shoe.price.replace("₹", "").trim()}"
            
            Glide.with(holder.itemView.context).load(shoe.imageUrl.optimizeCloudinaryUrl())
                .placeholder(R.drawable.shoesgreen3)
                .fitCenter()
                .into(holder.ivShoe)
                
            holder.itemView.setOnClickListener { onClick(shoe) }

            // Clean white/grey background for all product images
            holder.rlImageContainer.setBackgroundColor(android.graphics.Color.parseColor("#F9F9F9"))

            // Real discount badge from Firestore
            if (shoe.discountPercent > 0) {
                holder.cvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "-${shoe.discountPercent}%"
                holder.cvBadge.setCardBackgroundColor(android.graphics.Color.parseColor("#FF3B30"))
            } else if (position % 5 == 0) {
                holder.cvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "NEW"
                holder.cvBadge.setCardBackgroundColor(android.graphics.Color.parseColor("#7C4DFF"))
            } else {
                holder.cvBadge.visibility = View.GONE
            }

            // Real rating from Firestore reviews
            if (shoe.reviewCount > 0) {
                holder.tvRating.text = "⭐ ${String.format(java.util.Locale.US, "%.1f", shoe.avgRating)}"
                holder.tvSold.text = "${shoe.reviewCount} reviews"
            } else {
                holder.tvRating.text = "⭐ New"
                holder.tvSold.text = ""
            }

            // Quick Add to Cart
            holder.btnAddToCart.setOnClickListener {
                val ctx = holder.itemView.context
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    android.widget.Toast.makeText(ctx, "Please log in to add to cart", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val cartItem = hashMapOf(
                    "name"     to shoe.name,
                    "price"    to shoe.price,
                    "imageUrl" to shoe.imageUrl,
                    "quantity" to 1L,
                    "size"     to "UK 8"
                )
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("carts").document(uid)
                    .collection("items").document(shoe.id)
                    .set(cartItem)
                    .addOnSuccessListener {
                        android.widget.Toast.makeText(ctx, "✅ Added to cart!", android.widget.Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}
