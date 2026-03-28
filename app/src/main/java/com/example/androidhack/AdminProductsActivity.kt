package com.example.androidhack

import android.app.AlertDialog
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore

class AdminProductsActivity : AppCompatActivity() {

    data class ProductModel(
        val id: String = "",
        val name: String = "",
        val price: String = "",
        val imageUrl: String = "",
        val inStock: Boolean = true // Mock stock enhancement
    )

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: ProductAdapter
    private lateinit var rv: RecyclerView
    private var allProducts = listOf<ProductModel>()
    private var currentSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_products)

        findViewById<ImageView>(R.id.ivProductsBack).setOnClickListener { finish() }
        
        // Universal Add Routing
        val addProductClick = View.OnClickListener {
            startActivity(Intent(this, AdminAddProductActivity::class.java))
        }
        findViewById<TextView>(R.id.tvAddProduct).setOnClickListener(addProductClick)
        findViewById<FloatingActionButton>(R.id.fabAddProduct).setOnClickListener(addProductClick)

        rv = findViewById(R.id.rvAdminProducts)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ProductAdapter(emptyList(), db)
        rv.adapter = adapter

        // Search engine hook
        findViewById<EditText>(R.id.etProductsSearch).addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearch = s.toString().trim()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        loadProducts()
    }

    private fun loadProducts() {
        db.collection("products").get().addOnSuccessListener { snapshot ->
            allProducts = snapshot.documents.mapIndexed { index, doc ->
                ProductModel(
                    id       = doc.id,
                    name     = doc.getString("name") ?: "",
                    price    = doc.getString("price") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    // Simple mock constraint -> 1 out of every 4 products is out of stock. 
                    inStock  = index % 4 != 0 
                )
            }
            applyFilters()
        }
    }

    private fun applyFilters() {
        var filtered = allProducts
        if (currentSearch.isNotEmpty()) {
            filtered = filtered.filter { it.name.contains(currentSearch, ignoreCase = true) }
        }
        adapter.updateData(filtered)
    }

    class ProductAdapter(
        private var products: List<ProductModel>,
        private val db: FirebaseFirestore
    ) : RecyclerView.Adapter<ProductAdapter.ProductVH>() {

        class ProductVH(view: View) : RecyclerView.ViewHolder(view) {
            val ivImage: ImageView   = view.findViewById(R.id.ivAdminProductImage)
            val tvName: TextView     = view.findViewById(R.id.tvAdminProductName)
            val tvPrice: TextView    = view.findViewById(R.id.tvAdminProductPrice)
            val tvStatus: TextView   = view.findViewById(R.id.tvStockStatus)
            val ivEdit: ImageView    = view.findViewById(R.id.ivEditProduct)
            val ivDelete: ImageView  = view.findViewById(R.id.ivDeleteProduct)
        }

        fun updateData(newProducts: List<ProductModel>) {
            products = newProducts
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ProductVH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_product, parent, false))

        override fun getItemCount() = products.size

        override fun onBindViewHolder(holder: ProductVH, position: Int) {
            val product = products[position]
            holder.tvName.text  = product.name
            holder.tvPrice.text = product.price
            
            if (product.imageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context).load(product.imageUrl).centerCrop().into(holder.ivImage)
            } else {
                holder.ivImage.setImageResource(R.color.white)
            }

            // Map Stock Pill Colors Native Android
            if (product.inStock) {
                holder.tvStatus.text = "In Stock"
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                holder.tvStatus.text = "Out of Stock"
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E53935"))
            }

            // Edit Event Interceptor
            holder.ivEdit.setOnClickListener {
                Toast.makeText(holder.itemView.context, "Edit mode: Route loading for ${product.name}", Toast.LENGTH_SHORT).show()
                val intent = Intent(holder.itemView.context, AdminAddProductActivity::class.java)
                intent.putExtra("productId", product.id)
                holder.itemView.context.startActivity(intent)
            }

            // Delete Authorization Barrier
            holder.ivDelete.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Delete Warning")
                    .setMessage("Are you absolutely sure you want to permanently delete '${product.name}' from the store catalogue?")
                    .setPositiveButton("Delete") { _, _ ->
                        db.collection("products").document(product.id).delete()
                            .addOnSuccessListener {
                                Toast.makeText(holder.itemView.context, "Product permanently deleted", Toast.LENGTH_SHORT).show()
                                val updatedList = products.filter { it.id != product.id }
                                updateData(updatedList)
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
