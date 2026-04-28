package com.example.androidhack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    data class Shoe(
        val id: String = "",
        val name: String = "",
        val price: String = "",
        val imageUrl: String = "",
        val imageUrls: List<String> = emptyList(),
        val description: String = "",
        val specifications: String = "",
        val productStory: String = "",
        val features: String = "",
        val details: String = "",
        val avgRating: Double = 0.0,
        val reviewCount: Int = 0
    )

    // Banner promotional slides
    data class BannerSlide(
        val badge: String, val title: String, val discount: String,
        val couponCode: String, val imageRes: Int
    )
    private val bannerSlides = listOf(
        BannerSlide("🔥 Summer Sale", "Upgrade Your\nSneaker Game", "Up to 40% OFF", "SUMMER40", R.drawable.shoes211),
        BannerSlide("⚡ Limited Offer", "Premium Running\nCollection", "Up to 30% OFF", "RUN30", R.drawable.shoes013),
        BannerSlide("🎉 New Arrivals", "Step Into\nStyle Today", "Up to 25% OFF", "STYLE25", R.drawable.shoes015),
        BannerSlide("💎 Exclusive", "Luxury Formal\nFootwear", "Up to 35% OFF", "FORMAL35", R.drawable.shoesh1)
    )
    private lateinit var vpBanner: ViewPager2
    private lateinit var llBannerDots: LinearLayout
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var currentBannerPage = 0
    private val bannerRunnable = object : Runnable {
        override fun run() {
            currentBannerPage = (currentBannerPage + 1) % bannerSlides.size
            vpBanner.setCurrentItem(currentBannerPage, true)
            bannerHandler.postDelayed(this, 5000)
        }
    }

    private var allShoesList = listOf<Shoe>()
    private lateinit var onShoeClickGlobal: (Shoe) -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Request notification permission for Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val rvNewArrivals = findViewById<RecyclerView>(R.id.rvNewArrivals)
        val rvBestsellers = findViewById<RecyclerView>(R.id.rvBestsellers)
        val bottomNav     = findViewById<com.ismaeldivita.chipnavigation.ChipNavigationBar>(R.id.bottomNavigation)
        vpBanner          = findViewById(R.id.vpBanner)
        llBannerDots      = findViewById(R.id.llBannerDots)

        // ---- User greeting + avatar ----
        val tvGreeting      = findViewById<TextView>(R.id.tvGreeting)
        val ivUserAvatar    = findViewById<ImageView>(R.id.ivUserAvatar)
        val tvAvatarInitial = findViewById<TextView>(R.id.tvAvatarInitial)
        val currentUser     = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val displayName     = currentUser?.displayName?.split(" ")?.firstOrNull() ?: "User"
        tvGreeting.text = "Hi, $displayName 👋"

        // Always show initial letter by default (XML already sets visible)
        tvAvatarInitial.text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

        // If user has a Google/Facebook photo, load it and hide the initial
        val photoUri = currentUser?.photoUrl
        if (photoUri != null) {
            ivUserAvatar.visibility    = android.view.View.VISIBLE
            tvAvatarInitial.visibility = android.view.View.GONE
            Glide.with(this)
                .load(photoUri)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .into(ivUserAvatar)
        }

        // Avatar tap → Profile
        findViewById<androidx.cardview.widget.CardView>(R.id.cvAvatar).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Setup Banner Slider
        vpBanner.adapter = BannerAdapter(bannerSlides)
        setupBannerDots()
        bannerHandler.postDelayed(bannerRunnable, 3000)

        vpBanner.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentBannerPage = position
                updateBannerDots(position)
            }
        })

        // Banner arrow navigation
        findViewById<ImageView>(R.id.ivBannerLeft).setOnClickListener {
            currentBannerPage = if (currentBannerPage > 0) currentBannerPage - 1 else bannerSlides.size - 1
            vpBanner.setCurrentItem(currentBannerPage, true)
        }
        findViewById<ImageView>(R.id.ivBannerRight).setOnClickListener {
            currentBannerPage = (currentBannerPage + 1) % bannerSlides.size
            vpBanner.setCurrentItem(currentBannerPage, true)
        }

        // Cart button is now a CardView
        findViewById<androidx.cardview.widget.CardView>(R.id.ivCart).setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        // Tap on Search bar -> Go to Store
        findViewById<android.view.View>(R.id.cvSearch).setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }

        // View All Buttons
        findViewById<TextView>(R.id.tvViewAllNewArrivals).setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.putExtra("initial_sort", "new")
            startActivity(intent)
        }
        
        findViewById<TextView>(R.id.tvViewAllBestsellers).setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.putExtra("initial_sort", "bestseller")
            startActivity(intent)
        }

        // Category buttons → navigate to Store with type filter
        val categoryMap = mapOf(
            R.id.catRunning  to "running",
            R.id.catCasual   to "casual",
            R.id.catSports   to "sports",
            R.id.catFormal   to "formal",
            R.id.catOutdoor  to "outdoor"
        )
        categoryMap.forEach { (viewId, type) ->
            findViewById<View>(viewId).setOnClickListener {
                val intent = Intent(this, StoreActivity::class.java)
                intent.putExtra("initial_type", type)
                startActivity(intent)
            }
        }
        // "All" chip → opens Store with no filter
        findViewById<View>(R.id.catAll).setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }

        onShoeClickGlobal = { shoe ->
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

        val newArrivalsAdapter = ShoeAdapter(emptyList(), onShoeClickGlobal, false)
        val bestsellersAdapter = ShoeAdapter(emptyList(), onShoeClickGlobal, true)
        rvNewArrivals.adapter = newArrivalsAdapter
        rvBestsellers.adapter = bestsellersAdapter

        // Load from Firestore products collection
        FirebaseFirestore.getInstance().collection("products")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val allShoes = snapshot.documents.map { doc ->
                        val urls = (doc.get("imageUrls") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val primary = doc.getString("imageUrl") ?: ""
                        // Pick first non-video URL as thumbnail
                        val thumbnailUrl = urls.firstOrNull { !it.endsWith(".mp4") && !it.endsWith(".mov") && !it.endsWith(".webm") }
                            ?: if (!primary.endsWith(".mp4") && !primary.endsWith(".mov") && !primary.endsWith(".webm")) primary else ""
                        Shoe(
                            id             = doc.id,
                            name           = doc.getString("name") ?: "",
                            price          = doc.getString("price") ?: "",
                            imageUrl       = thumbnailUrl,
                            imageUrls      = urls.ifEmpty { if (primary.isNotEmpty()) listOf(primary) else emptyList() },
                            description    = doc.getString("description") ?: "",
                            specifications = doc.getString("specifications") ?: "",
                            productStory   = doc.getString("productStory") ?: "",
                            features       = doc.getString("features") ?: "",
                            details        = doc.getString("details") ?: "",
                            avgRating      = doc.getDouble("avgRating") ?: 0.0,
                            reviewCount    = doc.getLong("reviewCount")?.toInt() ?: 0
                        )
                    }
                    allShoesList = allShoes
                    // New Arrivals = last added (reversed)
                    newArrivalsAdapter.updateData(allShoes.reversed().take(10))
                    // Bestsellers = first few (original order)
                    bestsellersAdapter.updateData(allShoes.take(10))
                    
                    loadRecentlyViewed()
                } else {
                    // Fallback sample if no Firestore products yet
                    val fallback = listOf(
                        Shoe("1", "Nike Air Max 2025", "₹2999", "https://i.imgur.com/yMu7M8t.png"),
                        Shoe("2", "Adidas Ultra Boost", "₹3499", "https://i.imgur.com/yMu7M8t.png"),
                        Shoe("3", "Puma Sport Runner", "₹1999", "https://i.imgur.com/yMu7M8t.png")
                    )
                    newArrivalsAdapter.updateData(fallback)
                    bestsellersAdapter.updateData(fallback)
                }
            }

        // Bottom nav
        bottomNav.setItemSelected(R.id.nav_home, true)
        bottomNav.setOnItemSelectedListener { id ->
            if (id == R.id.nav_home) return@setOnItemSelectedListener
            val intent = when (id) {
                R.id.nav_shop -> Intent(this@HomeActivity, StoreActivity::class.java)
                R.id.nav_wishlist -> Intent(this@HomeActivity, WishlistActivity::class.java)
                R.id.nav_profile -> Intent(this@HomeActivity, ProfileActivity::class.java)
                else -> null
            }
            intent?.let {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startActivity(it)
                    overridePendingTransition(0, 0)
                }, 150)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerHandler.removeCallbacks(bannerRunnable)
    }

    override fun onResume() {
        super.onResume()
        updateCartBadge()
        loadRecentlyViewed()
    }

    private fun loadRecentlyViewed() {
        val prefs = getSharedPreferences("recently_viewed", android.content.Context.MODE_PRIVATE)
        val recentStr = prefs.getString("ids", "") ?: ""
        val rlRecentlyViewed = findViewById<android.view.View>(R.id.rlRecentlyViewed)
        val rvRecentlyViewed = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvRecentlyViewed)

        if (recentStr.isEmpty() || allShoesList.isEmpty()) {
            rlRecentlyViewed?.visibility = android.view.View.GONE
            rvRecentlyViewed?.visibility = android.view.View.GONE
            return
        }

        rlRecentlyViewed?.visibility = android.view.View.VISIBLE
        rvRecentlyViewed?.visibility = android.view.View.VISIBLE

        val recentIds = recentStr.split(",").filter { it.isNotEmpty() }

        // Match IDs and keep order
        val recentShoes = recentIds.mapNotNull { id -> allShoesList.find { it.id == id } }

        var adapter = rvRecentlyViewed?.adapter as? ShoeAdapter
        if (adapter == null) {
            adapter = ShoeAdapter(recentShoes, onShoeClickGlobal, true)
            rvRecentlyViewed?.adapter = adapter
        } else {
            adapter.updateData(recentShoes)
        }
    }

    private fun updateCartBadge() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val tvCartBadge = findViewById<android.widget.TextView>(R.id.tvCartBadge)
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("carts").document(uid).collection("items")
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

    private fun setupBannerDots() {
        llBannerDots.removeAllViews()
        bannerSlides.forEachIndexed { index, _ ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(20, 20).also {
                    it.setMargins(6, 0, 6, 0)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (index == 0) Color.parseColor("#7C4DFF") else Color.parseColor("#D0D0D0"))
                }
            }
            llBannerDots.addView(dot)
        }
    }

    private fun updateBannerDots(activeIndex: Int) {
        for (i in 0 until llBannerDots.childCount) {
            val dot = llBannerDots.getChildAt(i)
            (dot.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                if (i == activeIndex) Color.parseColor("#7C4DFF") else Color.parseColor("#D0D0D0")
            )
        }
    }

    // Promotional Banner Adapter for ViewPager2
    inner class BannerAdapter(private val slides: List<BannerSlide>) :
        RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {
        inner class BannerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivSlide: ImageView = view.findViewById(R.id.ivBannerSlide)
            val tvBadge: TextView = view.findViewById(R.id.tvBannerBadge)
            val tvTitle: TextView = view.findViewById(R.id.tvBannerTitle)
            val tvDiscount: TextView = view.findViewById(R.id.tvBannerDiscount)
            val tvCoupon: TextView = view.findViewById(R.id.tvCouponCode)
            val btnShopNow: TextView = view.findViewById(R.id.btnShopNow)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            BannerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_banner_slide, parent, false))
        override fun getItemCount() = slides.size
        override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
            val slide = slides[position]
            holder.ivSlide.setImageResource(slide.imageRes)
            holder.tvBadge.text = slide.badge
            holder.tvTitle.text = slide.title
            holder.tvDiscount.text = slide.discount
            holder.tvCoupon.text = slide.couponCode
            holder.btnShopNow.setOnClickListener {
                startActivity(Intent(this@HomeActivity, StoreActivity::class.java))
            }
        }
    }

    class ShoeAdapter(
        private var shoes: List<Shoe>,
        private val onClick: (Shoe) -> Unit,
        private val isBestseller: Boolean = false
    ) : RecyclerView.Adapter<ShoeAdapter.ShoeViewHolder>() {

        fun updateData(newShoes: List<Shoe>) { shoes = newShoes; notifyDataSetChanged() }

        class ShoeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView  = view.findViewById(R.id.tvShoeName)
            val tvPrice: TextView = view.findViewById(R.id.tvShoePrice)
            val ivShoe: ImageView = view.findViewById(R.id.ivShoe)
            val rlImageContainer: View = view.findViewById(R.id.rlImageContainer)
            val cvBadge: View = view.findViewById(R.id.cvBadge)
            val tvBadge: TextView = view.findViewById(R.id.tvBadge)
            val tvRating: TextView = view.findViewById(R.id.tvRating)
            val tvSold: TextView = view.findViewById(R.id.tvSold)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ShoeViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_shoe, parent, false))

        override fun getItemCount() = shoes.size

        override fun onBindViewHolder(holder: ShoeViewHolder, position: Int) {
            val shoe = shoes[position]
            holder.tvName.text  = shoe.name
            holder.tvPrice.text = "₹${shoe.price.replace("₹", "").trim()}"
            
            Glide.with(holder.itemView.context)
                .load(shoe.imageUrl.optimizeCloudinaryUrl())
                .placeholder(R.drawable.shoesgreen3)
                .error(R.drawable.shoesgreen3)
                .fitCenter()
                .into(holder.ivShoe)
                
            holder.itemView.setOnClickListener { onClick(shoe) }

            // Dynamic logic for UI enhancements
            if (isBestseller) {
                holder.cvBadge.visibility = View.GONE
                holder.rlImageContainer.setBackgroundColor(android.graphics.Color.parseColor("#F9F9F9"))
            } else {
                holder.cvBadge.visibility = if (position < 3) View.VISIBLE else View.GONE
                holder.tvBadge.text = "NEW"
                holder.rlImageContainer.setBackgroundColor(android.graphics.Color.parseColor("#F9F9F9"))
            }

            // Real rating from Firestore reviews
            if (shoe.reviewCount > 0) {
                holder.tvRating.text = "⭐ ${String.format(java.util.Locale.US, "%.1f", shoe.avgRating)}"
                holder.tvSold.text = " | ${shoe.reviewCount} reviews"
            } else {
                holder.tvRating.text = "⭐ New"
                holder.tvSold.text = ""
            }
        }
    }
}
