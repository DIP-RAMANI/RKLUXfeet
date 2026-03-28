package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WishlistActivity : AppCompatActivity() {

    data class Shoe(
        val id: String = "", 
        val name: String = "", 
        val price: String = "", 
        val imageUrl: String = "",
        val description: String = "",
        val specifications: String = ""
    )

    private val db  = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wishlist)

        val rvWishlistGrid = findViewById<RecyclerView>(R.id.rvWishlistGrid)
        val bottomNav      = findViewById<com.google.android.material.tabs.TabLayout>(R.id.bottomNavigation)
        val tvEmpty        = try { findViewById<TextView>(R.id.tvWishlistEmpty) } catch (e: Exception) { null }

        findViewById<ImageView>(R.id.ivBackWishlist).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.ivCart)?.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        rvWishlistGrid.layoutManager = GridLayoutManager(this, 2)

        val adapter = WishlistAdapter(emptyList()) { shoe ->
            val intent = Intent(this, ProductDetailsActivity::class.java).apply {
                putExtra("productId", shoe.id)
                putExtra("productName", shoe.name)
                putExtra("productPrice", shoe.price)
                putExtra("productImage", shoe.imageUrl)
                putExtra("productDesc", shoe.description)
                putExtra("productSpecs", shoe.specifications)
            }
            startActivity(intent)
        }
        rvWishlistGrid.adapter = adapter

        // Load wishlist from Firestore
        val uid = uid
        if (uid != null) {
            db.collection("wishlists").document(uid).collection("items")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val shoes = snapshot.documents.map { doc ->
                            Shoe(
                                id             = doc.getString("id") ?: doc.id,
                                name           = doc.getString("name") ?: "",
                                price          = doc.getString("price") ?: "",
                                imageUrl       = doc.getString("imageUrl") ?: "",
                                description    = doc.getString("description") ?: "",
                                specifications = doc.getString("specifications") ?: ""
                            )
                        }
                        adapter.updateData(shoes)
                        tvEmpty?.visibility = if (shoes.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
        } else {
            tvEmpty?.visibility = View.VISIBLE
        }

        // Bottom nav
        bottomNav.getTabAt(2)?.select()
        bottomNav.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                if (tab?.position == 2) return
                val intent = when (tab?.position) {
                    0 -> Intent(this@WishlistActivity, HomeActivity::class.java)
                    1 -> Intent(this@WishlistActivity, StoreActivity::class.java)
                    3 -> Intent(this@WishlistActivity, ProfileActivity::class.java)
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

    class WishlistAdapter(
        private var shoes: List<Shoe>,
        private val onClick: (Shoe) -> Unit
    ) : RecyclerView.Adapter<WishlistAdapter.ViewHolder>() {

        fun updateData(newShoes: List<Shoe>) { shoes = newShoes; notifyDataSetChanged() }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView  = view.findViewById(R.id.tvGridShoeName)
            val tvPrice: TextView = view.findViewById(R.id.tvGridShoePrice)
            val ivShoe: ImageView = view.findViewById(R.id.ivGridShoe)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_shoe_grid, parent, false))

        override fun getItemCount() = shoes.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val shoe = shoes[position]
            holder.tvName.text  = shoe.name
            holder.tvPrice.text = "₹${shoe.price.replace("₹", "").trim()}"
            Glide.with(holder.itemView.context).load(shoe.imageUrl)
                .placeholder(R.drawable.shoesgreen3).centerCrop().into(holder.ivShoe)
            holder.itemView.setOnClickListener { onClick(shoe) }
        }
    }
}
