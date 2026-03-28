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
        val brand: String = "",
        val timestamp: Long = 0L,
        val popularity: Int = 0,
        val description: String = "",
        val specifications: String = ""
    )

    private lateinit var adapter: GridShoeAdapter
    private var allShoes = listOf<Shoe>()
    private var currentSort = "all"
    private var currentBrand = "all"
    private var currentSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)

        val rvStoreGrid = findViewById<RecyclerView>(R.id.rvStoreGrid)
        val bottomNav = findViewById<com.google.android.material.tabs.TabLayout>(R.id.bottomNavigation)
        val tvCount = findViewById<TextView>(R.id.tvResultsCount)

        findViewById<ImageView>(R.id.ivBackStore).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivCart)?.setOnClickListener { 
            startActivity(Intent(this, CartActivity::class.java)) 
        }

        adapter = GridShoeAdapter(emptyList()) { shoe ->
            val intent = Intent(this, ProductDetailsActivity::class.java).apply {
                putExtra("productId",     shoe.id)
                putExtra("productName",   shoe.name)
                putExtra("productPrice",  shoe.price)
                putExtra("productImage",  shoe.imageUrl)
                putExtra("productDesc",   shoe.description)
                putExtra("productSpecs",  shoe.specifications)
            }
            startActivity(intent)
        }
        rvStoreGrid.adapter = adapter

        // Load products from Firestore
        FirebaseFirestore.getInstance().collection("products")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    allShoes = snapshot.documents.map { doc ->
                        Shoe(
                            id         = doc.id,
                            name       = doc.getString("name") ?: "",
                            price      = doc.getString("price") ?: "",
                            imageUrl   = doc.getString("imageUrl") ?: "",
                            brand      = doc.getString("brand") ?: "", // Assuming 'brand' field exists in Firestore
                            timestamp  = doc.getLong("timestamp") ?: 0L,
                            popularity = doc.getLong("popularity")?.toInt() ?: 0,
                            description    = doc.getString("description") ?: "",
                            specifications = doc.getString("specifications") ?: ""
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

        // Sort chips
        setupChip(R.id.chipAll, "all")
        setupChip(R.id.chipPriceLow, "price_low")
        setupChip(R.id.chipPriceHigh, "price_high")
        setupChip(R.id.chipNewArrivals, "new")
        setupChip(R.id.chipBestseller, "bestseller")

        val initialSort = intent.getStringExtra("initial_sort") ?: "all"
        when(initialSort) {
            "new" -> findViewById<TextView>(R.id.chipNewArrivals).performClick()
            "bestseller" -> findViewById<TextView>(R.id.chipBestseller).performClick()
            else -> findViewById<TextView>(R.id.chipAll).performClick()
        }

        // Brand chips
        setupBrandChip(R.id.brandAll, "all")
        setupBrandChip(R.id.brandNike, "nike")
        setupBrandChip(R.id.brandAdidas, "adidas")
        setupBrandChip(R.id.brandPuma, "puma")
        setupBrandChip(R.id.brandAsics, "asics")
        setupBrandChip(R.id.brandOther, "other")

        // Bottom nav
        bottomNav.getTabAt(1)?.select()
        bottomNav.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                if (tab?.position == 1) return
                val intent = when (tab?.position) {
                    0 -> Intent(this@StoreActivity, HomeActivity::class.java)
                    2 -> Intent(this@StoreActivity, WishlistActivity::class.java)
                    3 -> Intent(this@StoreActivity, ProfileActivity::class.java)
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
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupChip(id: Int, sort: String) {
        val allChipIds = listOf(R.id.chipAll, R.id.chipPriceLow, R.id.chipPriceHigh, R.id.chipNewArrivals, R.id.chipBestseller)
        findViewById<TextView>(id).setOnClickListener {
            currentSort = sort
            allChipIds.forEach { chipId ->
                val chip = findViewById<TextView>(chipId)
                if (chipId == id) {
                    chip.setBackgroundResource(R.drawable.bg_chip_active)
                    chip.setTextColor(android.graphics.Color.WHITE)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_inactive)
                    chip.setTextColor(android.graphics.Color.BLACK)
                }
            }
            applyFiltersAndSort()
        }
    }

    private fun setupBrandChip(id: Int, brand: String) {
        val allBrandIds = listOf(R.id.brandAll, R.id.brandNike, R.id.brandAdidas, R.id.brandPuma, R.id.brandAsics, R.id.brandOther)
        findViewById<TextView>(id).setOnClickListener {
            currentBrand = brand
            allBrandIds.forEach { chipId ->
                val chip = findViewById<TextView>(chipId)
                if (chipId == id) {
                    chip.setBackgroundResource(R.drawable.bg_chip_outline_active)
                    chip.setTextColor(android.graphics.Color.BLACK)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_outline_inactive)
                    chip.setTextColor(android.graphics.Color.parseColor("#757575"))
                }
            }
            applyFiltersAndSort()
        }
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

        // Brand filter
        if (currentBrand != "all") {
            filtered = if (currentBrand == "other") {
                filtered.filter { shoe ->
                    val lower = shoe.name.lowercase()
                    !lower.contains("nike") && !lower.contains("adidas") &&
                    !lower.contains("puma") && !lower.contains("asics")
                }
            } else {
                filtered.filter { it.name.contains(currentBrand, ignoreCase = true) }
            }
        }

        // Sort
        filtered = when (currentSort) {
            "price_low"   -> filtered.sortedBy { parsePrice(it.price) }
            "price_high"  -> filtered.sortedByDescending { parsePrice(it.price) }
            "new"         -> filtered.reversed()   // newest = last added = last in list
            "bestseller"  -> filtered              // Static order = bestseller order
            else          -> filtered
        }

        adapter.updateData(filtered)
        findViewById<TextView>(R.id.tvResultsCount).text = "${filtered.size} results"
    }

    // Adapter with click callback
    override fun onResume() {
        super.onResume()
        updateCartBadge()
    }

    private fun updateCartBadge() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val tvCartBadge = findViewById<android.widget.TextView>(R.id.tvCartBadge)
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("cart")
            .get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.size()
                if (count > 0) {
                    tvCartBadge.visibility = android.view.View.VISIBLE
                    tvCartBadge.text = count.toString()
                } else {
                    tvCartBadge.visibility = android.view.View.GONE
                }
            }
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
            val tvBadge: TextView = view.findViewById(R.id.tvGridBadge)
            val tvRating: TextView = view.findViewById(R.id.tvGridRating)
            val tvSold: TextView = view.findViewById(R.id.tvGridSold)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_shoe_grid, parent, false))

        override fun getItemCount() = shoes.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val shoe = shoes[position]
            holder.tvName.text  = shoe.name
            holder.tvPrice.text = "₹${shoe.price.replace("₹", "").trim()}"
            
            Glide.with(holder.itemView.context).load(shoe.imageUrl)
                .placeholder(R.drawable.shoesgreen3)
                .fitCenter()
                .into(holder.ivShoe)
                
            holder.itemView.setOnClickListener { onClick(shoe) }

            val colors = arrayOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.homePastelGreen),
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.homePastelPink),
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.homePastelBlue),
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.homePastelYellow)
            )
            // Use ID hash to keep colors consistent for the same item across layout refreshes
            val colorIndex = Math.abs(shoe.id.hashCode()) % colors.size
            holder.rlImageContainer.setBackgroundColor(colors[colorIndex])

            // Randomize Discount/New Badge
            if (position % 4 == 0) {
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "-20%"
                holder.tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#FF3B30"))
            } else if (position % 5 == 0) {
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "NEW"
                holder.tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#7C4DFF"))
            } else {
                holder.tvBadge.visibility = View.GONE
            }

            // Fake rating & sold logic
            val fakeRating = 4.0 + (5 - (position % 5)) * 0.1
            holder.tvRating.text = "⭐ ${String.format(java.util.Locale.US, "%.1f", fakeRating)}"
            val sold = 50 + (position * 12) % 300
            holder.tvSold.text = " | $sold sold"
        }
    }
}
