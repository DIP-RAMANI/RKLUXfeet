package com.example.androidhack

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class AdminOrdersActivity : AppCompatActivity() {

    data class OrderModel(
        val id: String = "",
        val userId: String = "",
        val userName: String = "",
        val address: String = "",
        val paymentMethod: String = "",
        val status: String = "Pending",
        val totalAmount: Double = 0.0,
        val firstProductName: String = "",
        val firstProductImage: String = "",
        val extraItemsCount: Int = 0
    )

    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: OrderAdapter
    private var allOrders = listOf<OrderModel>()
    
    // Filters
    private var currentFilter = "All"
    private var currentSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_orders)

        findViewById<ImageView>(R.id.ivOrdersBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvOrders)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = OrderAdapter(emptyList(), db)
        rv.adapter = adapter

        // Search Engine
        findViewById<EditText>(R.id.etOrdersSearch).addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearch = s.toString().trim()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Status Chips
        setupFilterChip(R.id.chipFilterAll, "All")
        setupFilterChip(R.id.chipFilterPending, "Pending")
        setupFilterChip(R.id.chipFilterShipped, "Shipped")
        setupFilterChip(R.id.chipFilterDelivered, "Delivered")
        setupFilterChip(R.id.chipFilterCancelled, "Cancelled")

        loadOrders()
    }

    private fun setupFilterChip(id: Int, filterStatus: String) {
        val allChips = listOf(
            R.id.chipFilterAll, 
            R.id.chipFilterPending, 
            R.id.chipFilterShipped, 
            R.id.chipFilterDelivered, 
            R.id.chipFilterCancelled
        )
        findViewById<TextView>(id).setOnClickListener {
            currentFilter = filterStatus
            allChips.forEach { chipId ->
                val chip = findViewById<TextView>(chipId)
                if (chipId == id) {
                    chip.setBackgroundResource(R.drawable.bg_chip_active)
                    chip.setTextColor(Color.WHITE)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_inactive)
                    chip.setTextColor(Color.BLACK)
                }
            }
            applyFilters()
        }
    }

    private fun loadOrders() {
        db.collection("orders").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                allOrders = snapshot.documents.map { doc ->
                    val itemsList = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val firstItem = itemsList.firstOrNull()
                    val pName = firstItem?.get("name") as? String ?: "Unknown Product"
                    val pImage = firstItem?.get("imageUrl") as? String ?: ""
                    val extraCount = if (itemsList.size > 1) itemsList.size - 1 else 0

                    OrderModel(
                        id           = doc.id,
                        userId       = doc.getString("userId") ?: "",
                        userName     = doc.getString("customerName") ?: doc.getString("userName") ?: "Unknown User",
                        address      = doc.getString("address") ?: "No address provided",
                        paymentMethod= doc.getString("paymentMethod") ?: "N/A",
                        status       = doc.getString("status") ?: "Pending",
                        totalAmount  = doc.getDouble("totalAmount") ?: 0.0,
                        firstProductName = pName,
                        firstProductImage = pImage,
                        extraItemsCount = extraCount
                    )
                }
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        var filtered = allOrders

        // Apply Search Filter
        if (currentSearch.isNotEmpty()) {
            filtered = filtered.filter { 
                it.userName.contains(currentSearch, true) || 
                it.firstProductName.contains(currentSearch, true) 
            }
        }

        // Apply Pill Filter
        if (currentFilter != "All") {
            filtered = filtered.filter { it.status.equals(currentFilter, true) }
        }

        val pendingCount = allOrders.count { it.status.equals("Pending", true) }
        findViewById<TextView>(R.id.tvOrderCount).text = "$pendingCount pending"

        adapter.updateData(filtered)
    }

    class OrderAdapter(
        private var orders: List<OrderModel>,
        private val db: FirebaseFirestore
    ) : RecyclerView.Adapter<OrderAdapter.OrderVH>() {

        class OrderVH(view: View) : RecyclerView.ViewHolder(view) {
            val ivImage: ImageView      = view.findViewById(R.id.ivOrderProductImage)
            val tvName: TextView        = view.findViewById(R.id.tvOrderProductName)
            val tvUser: TextView        = view.findViewById(R.id.tvOrderUserName)
            val tvAddress: TextView     = view.findViewById(R.id.tvOrderAddress)
            val tvPayment: TextView     = view.findViewById(R.id.tvOrderPaymentMethod)
            val tvPrice: TextView       = view.findViewById(R.id.tvOrderPrice)
            val tvStatus: TextView      = view.findViewById(R.id.tvOrderStatus)
            val btnPending: MaterialButton   = view.findViewById(R.id.btnSetPending)
            val btnShipped: MaterialButton   = view.findViewById(R.id.btnSetShipped)
            val btnDelivered: MaterialButton = view.findViewById(R.id.btnSetDelivered)
            val btnCancelled: MaterialButton = view.findViewById(R.id.btnSetCancelled)
        }

        fun updateData(newOrders: List<OrderModel>) {
            orders = newOrders
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            OrderVH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_order, parent, false))

        override fun getItemCount() = orders.size

        override fun onBindViewHolder(holder: OrderVH, position: Int) {
            val order = orders[position]
            
            val displayName = if (order.extraItemsCount > 0) {
                "${order.firstProductName} (+${order.extraItemsCount} more)"
            } else {
                order.firstProductName
            }
            
            holder.tvName.text  = displayName
            holder.tvUser.text  = "Customer: ${order.userName}"
            holder.tvAddress.text = "Address: ${order.address}"
            holder.tvPayment.text = "Payment: ${order.paymentMethod}"
            holder.tvPrice.text = "₹${order.totalAmount.toInt()}"
            holder.tvStatus.text = order.status

            val statusColor = when (order.status) {
                "Pending"   -> Color.parseColor("#E53935") // Red
                "Shipped"   -> Color.parseColor("#FB8C00") // Orange
                "Delivered" -> Color.parseColor("#43A047") // Green
                "Cancelled" -> Color.parseColor("#757575") // Gray
                else        -> Color.parseColor("#1A1A2E") 
            }
            holder.tvStatus.backgroundTintList = ColorStateList.valueOf(statusColor)

            if (order.firstProductImage.isNotEmpty()) {
                Glide.with(holder.itemView.context).load(order.firstProductImage).centerCrop().into(holder.ivImage)
            } else {
                holder.ivImage.setImageResource(R.color.white)
            }

            val triggerStatusUpdate = { newStatus: String ->
                // Confirmation Dialog Enhancement
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Update Status")
                    .setMessage("Are you sure you want to mark this order as $newStatus?")
                    .setPositiveButton("Yes") { _, _ ->
                        db.collection("orders").document(order.id).update("status", newStatus)
                            .addOnSuccessListener {
                                Toast.makeText(holder.itemView.context, "Moved to $newStatus!", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            holder.btnPending.setOnClickListener   { triggerStatusUpdate("Pending") }
            holder.btnShipped.setOnClickListener   { triggerStatusUpdate("Shipped") }
            holder.btnDelivered.setOnClickListener { triggerStatusUpdate("Delivered") }
            holder.btnCancelled.setOnClickListener { triggerStatusUpdate("Cancelled") }

            // Allow admin to click the order card to see full details
            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, OrderDetailsActivity::class.java)
                intent.putExtra("orderId", order.id)
                holder.itemView.context.startActivity(intent)
            }
        }
    }
}
