package com.example.androidhack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class AdminReturnsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_returns)

        findViewById<ImageView>(R.id.ivBackReturns).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvReturnRequests)
        val tvEmpty = findViewById<TextView>(R.id.tvNoReturns)
        rv.layoutManager = LinearLayoutManager(this)

        db.collection("returnRequests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null || snap.isEmpty) {
                    rv.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                data class ReturnRequest(
                    val id: String,
                    val orderId: String,
                    val shortOrderId: String,
                    val type: String,
                    val reason: String,
                    val notes: String,
                    val status: String,
                    val createdAt: Timestamp?
                )

                val items = snap.documents.map { doc ->
                    ReturnRequest(
                        id           = doc.id,
                        orderId      = doc.getString("orderId") ?: "",
                        shortOrderId = doc.getString("shortOrderId") ?: "",
                        type         = doc.getString("type") ?: "Return",
                        reason       = doc.getString("reason") ?: "",
                        notes        = doc.getString("notes") ?: "",
                        status       = doc.getString("status") ?: "Pending",
                        createdAt    = doc.getTimestamp("createdAt")
                    )
                }

                rv.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE

                rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
                        val tvType: TextView    = view.findViewById(R.id.tvReturnType)
                        val tvStatus: TextView  = view.findViewById(R.id.tvReturnStatus)
                        val tvOrderId: TextView = view.findViewById(R.id.tvReturnOrderId)
                        val tvReason: TextView  = view.findViewById(R.id.tvReturnReason)
                        val tvNotes: TextView   = view.findViewById(R.id.tvReturnNotes)
                        val tvDate: TextView    = view.findViewById(R.id.tvReturnDate)
                        val btnApprove: Button  = view.findViewById(R.id.btnApproveReturn)
                        val btnReject: Button   = view.findViewById(R.id.btnRejectReturn)
                    }

                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_return_request, parent, false))

                    override fun getItemCount() = items.size

                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                        holder as VH
                        val req = items[position]
                        holder.tvType.text    = if (req.type == "Exchange") "🔄 Exchange" else "↩ Return"
                        holder.tvOrderId.text = "Order #${req.shortOrderId.ifEmpty { req.orderId.take(8) }}"
                        holder.tvReason.text  = "Reason: ${req.reason}"
                        holder.tvNotes.text   = if (req.notes.isNotEmpty()) "\"${req.notes}\"" else ""
                        holder.tvDate.text    = req.createdAt?.let {
                            "Submitted: " + SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it.toDate())
                        } ?: ""

                        // Status badge
                        holder.tvStatus.text = req.status
                        val badgeColor = when (req.status.lowercase()) {
                            "approved" -> "#4CAF50"
                            "rejected" -> "#E53935"
                            else       -> "#FF9800"
                        }
                        holder.tvStatus.backgroundTintList =
                            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(badgeColor))

                        // Disable buttons if already resolved
                        val isPending = req.status.equals("Pending", ignoreCase = true)
                        holder.btnApprove.isEnabled = isPending
                        holder.btnReject.isEnabled  = isPending
                        holder.btnApprove.alpha = if (isPending) 1f else 0.4f
                        holder.btnReject.alpha  = if (isPending) 1f else 0.4f

                        holder.btnApprove.setOnClickListener {
                            updateReturnStatus(req.id, "Approved")
                        }
                        holder.btnReject.setOnClickListener {
                            updateReturnStatus(req.id, "Rejected")
                        }
                    }
                }
            }
    }

    private fun updateReturnStatus(requestId: String, newStatus: String) {
        db.collection("returnRequests").document(requestId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Request marked as $newStatus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }
}
