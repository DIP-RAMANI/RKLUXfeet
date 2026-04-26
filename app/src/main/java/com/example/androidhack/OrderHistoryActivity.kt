package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class OrderHistoryActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var rvOrders: RecyclerView
    private lateinit var adapter: OrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_history)

        findViewById<ImageView>(R.id.ivBackOrder).setOnClickListener {
            finish()
        }

        rvOrders = findViewById(R.id.rvOrders)
        rvOrders.layoutManager = LinearLayoutManager(this)
        
        adapter = OrderAdapter(emptyList()) { orderId ->
            val intent = Intent(this, OrderDetailsActivity::class.java)
            intent.putExtra("orderId", orderId)
            startActivity(intent)
        }
        rvOrders.adapter = adapter
        
        loadOrders()
    }
    
    private fun loadOrders() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please log in to view orders", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("orders")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load orders", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val ordersList = snapshot.documents.map { doc ->
                        val orderId = doc.getString("orderId") ?: doc.id
                        val totalAmount = doc.getDouble("totalAmount") ?: 0.0
                        val status = doc.getString("status") ?: "Pending"
                        val timestamp = doc.getTimestamp("createdAt")

                        // Extract the first item's imageUrl so each row shows its own product thumbnail
                        val itemsList = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                        val firstImageUrl = (itemsList.firstOrNull()?.get("imageUrl") as? String) ?: ""

                        var dateStr = "Unknown Date"
                        var timeMs = 0L
                        if (timestamp != null) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            dateStr = sdf.format(timestamp.toDate())
                            timeMs = timestamp.toDate().time
                        }

                        OrderItem(
                            orderId = orderId,
                            number = "Order #${orderId.take(8)}...",
                            dateStatus = "Date: $dateStr | Status: $status",
                            total = "Total: ₹${totalAmount.toInt()}",
                            timestampMs = timeMs,
                            firstImageUrl = firstImageUrl
                        )
                    }.sortedByDescending { it.timestampMs }
                    
                    adapter.updateData(ordersList)
                }
            }
    }

    data class OrderItem(
        val orderId: String,
        val number: String,
        val dateStatus: String,
        val total: String,
        val timestampMs: Long,
        val firstImageUrl: String = ""
    )

    class OrderAdapter(
        private var orders: List<OrderItem>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<OrderAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNumber: TextView = view.findViewById(R.id.tvOrderNumber)
            val tvDateStatus: TextView = view.findViewById(R.id.tvOrderDateStatus)
            val tvTotal: TextView = view.findViewById(R.id.tvOrderTotal)
            val ivOrderImage: ImageView = view.findViewById(R.id.ivOrderImage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = orders[position]
            holder.tvNumber.text = order.number
            holder.tvDateStatus.text = order.dateStatus
            holder.tvTotal.text = order.total

            // Load the actual product thumbnail purchased in that order
            if (order.firstImageUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(order.firstImageUrl.optimizeCloudinaryUrl())
                    .centerCrop()
                    .placeholder(R.drawable.ic_package)
                    .error(R.drawable.ic_package)
                    .into(holder.ivOrderImage)
            } else {
                holder.ivOrderImage.setImageResource(R.drawable.ic_package)
            }

            holder.itemView.setOnClickListener {
                onClick(order.orderId)
            }
        }

        override fun getItemCount() = orders.size
        
        fun updateData(newOrders: List<OrderItem>) {
            orders = newOrders
            notifyDataSetChanged()
        }
    }
}
