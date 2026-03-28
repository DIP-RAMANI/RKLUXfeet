package com.example.androidhack

import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.androidhack.models.CartItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CartActivity : AppCompatActivity() {

    private lateinit var rvCartItems: RecyclerView
    private lateinit var adapter: CartAdapter
    private lateinit var tvSubtotalAmount: TextView
    private lateinit var tvTaxesAmount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var llEmptyCart: LinearLayout
    private lateinit var llFilledCartElements: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private var cartListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        findViewById<ImageView>(R.id.ivBackCart).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCheckoutCart).setOnClickListener {
            if (adapter.itemCount == 0) {
                Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CheckoutActivity::class.java))
        }

        tvSubtotalAmount = findViewById(R.id.tvSubtotalAmount)
        tvTaxesAmount = findViewById(R.id.tvTaxesAmount)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        llEmptyCart = findViewById(R.id.llEmptyCart)
        llFilledCartElements = findViewById(R.id.llFilledCartElements)

        rvCartItems = findViewById(R.id.rvCartItems)
        rvCartItems.layoutManager = LinearLayoutManager(this)

        adapter = CartAdapter(emptyList()) { item, action ->
            when (action) {
                "delete" -> deleteCartItem(item)
                "plus"   -> updateCartItemQuantity(item, item.quantity + 1)
                "minus"  -> if (item.quantity > 1) updateCartItemQuantity(item, item.quantity - 1) else deleteCartItem(item)
            }
        }
        rvCartItems.adapter = adapter

        loadCart()
    }

    private fun loadCart() {
        if (uid == null) {
            Toast.makeText(this, "Please log in to view cart", Toast.LENGTH_SHORT).show()
            updateVisibilityAndSummary(emptyList())
            return
        }

        cartListener = db.collection("carts").document(uid!!)
            .collection("items")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading cart", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val items = snapshot.documents.map { doc ->
                        CartItem(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            price = doc.getString("price") ?: "0",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            quantity = doc.getLong("quantity") ?: 1L
                        )
                    }
                    adapter.updateData(items)
                    updateVisibilityAndSummary(items)
                }
            }
    }

    private fun updateVisibilityAndSummary(items: List<CartItem>) {
        if (items.isEmpty()) {
            llEmptyCart.visibility = View.VISIBLE
            llFilledCartElements.visibility = View.GONE
        } else {
            llEmptyCart.visibility = View.GONE
            llFilledCartElements.visibility = View.VISIBLE
        }

        var subtotal = 0.0
        for (item in items) {
            val priceStr = item.price.replace("[^\\d.]".toRegex(), "")
            val priceVal = priceStr.toDoubleOrNull() ?: 0.0
            subtotal += (priceVal * item.quantity)
        }

        val taxes = subtotal * 0.05 // 5% tax
        val total = subtotal + taxes

        tvSubtotalAmount.text = "₹${subtotal.toInt()}"
        tvTaxesAmount.text = "₹${taxes.toInt()}"
        tvTotalAmount.text = "₹${total.toInt()}"
    }

    private fun updateCartItemQuantity(item: CartItem, newQuantity: Long) {
        if (uid == null) return
        db.collection("carts").document(uid!!)
            .collection("items").document(item.id)
            .update("quantity", newQuantity)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update quantity", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteCartItem(item: CartItem) {
        if (uid == null) return
        db.collection("carts").document(uid!!)
            .collection("items").document(item.id)
            .delete()
            .addOnFailureListener {
                Toast.makeText(this, "Failed to remove item", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cartListener?.remove()
    }

    class CartAdapter(
        private var items: List<CartItem>,
        private val onActionClick: (CartItem, String) -> Unit
    ) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView        = view.findViewById(R.id.tvCartItemName)
            val tvQty: TextView         = view.findViewById(R.id.tvCartItemQuantity)
            val tvPrice: TextView       = view.findViewById(R.id.tvCartItemPrice)
            val ivItem: ImageView       = view.findViewById(R.id.ivCartItem)
            val ivDelete: ImageView     = view.findViewById(R.id.ivDeleteCartItem)
            val btnMinus: TextView      = view.findViewById(R.id.btnMinusQuantity)
            val btnPlus: TextView       = view.findViewById(R.id.btnPlusQuantity)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvQty.text = item.quantity.toString()
            
            val priceNum = item.price.replace("[^\\d.]".toRegex(), "")
            holder.tvPrice.text = "₹$priceNum"

            if (item.imageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.shoesgreen3)
                    .centerCrop()
                    .into(holder.ivItem)
            } else {
                holder.ivItem.setImageResource(R.drawable.shoesgreen3)
            }

            holder.btnMinus.setOnClickListener { onActionClick(item, "minus") }
            holder.btnPlus.setOnClickListener { onActionClick(item, "plus") }
            holder.ivDelete.setOnClickListener { onActionClick(item, "delete") }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<CartItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
