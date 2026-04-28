package com.example.androidhack

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
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminNotificationsActivity : AppCompatActivity() {

    data class NotificationModel(
        val id: String = "",
        val title: String = "",
        val message: String = "",
        val type: String = "offer",
        val sentAt: Date? = null
    )

    private val db = FirebaseFirestore.getInstance()
    private var selectedType = "offer"
    private lateinit var adapter: NotificationHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_notifications)

        // Back button
        findViewById<ImageView>(R.id.ivNotifBack).setOnClickListener { finish() }

        // Input fields
        val etTitle = findViewById<EditText>(R.id.etNotifTitle)
        val etMessage = findViewById<EditText>(R.id.etNotifMessage)
        val tvPreviewTitle = findViewById<TextView>(R.id.tvPreviewTitle)
        val tvPreviewMessage = findViewById<TextView>(R.id.tvPreviewMessage)
        val tvPreviewType = findViewById<TextView>(R.id.tvPreviewType)
        val tvPreviewIcon = findViewById<TextView>(R.id.tvPreviewIcon)

        // Live preview sync
        etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvPreviewTitle.text = if (s.isNullOrBlank()) "Notification Title" else s
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvPreviewMessage.text = if (s.isNullOrBlank()) "Your notification message will appear here..." else s
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Type chips
        val typeChips = listOf(
            R.id.chipTypeOffer to "offer",
            R.id.chipTypePromo to "promo_code",
            R.id.chipTypeNewArrival to "new_arrival",
            R.id.chipTypeGeneral to "general"
        )
        val typeIcons = mapOf(
            "offer" to "🏷️",
            "promo_code" to "🎟️",
            "new_arrival" to "👟",
            "general" to "📢"
        )
        val typeLabels = mapOf(
            "offer" to "Offer",
            "promo_code" to "Promo Code",
            "new_arrival" to "New Arrival",
            "general" to "General"
        )

        typeChips.forEach { (chipId, type) ->
            findViewById<TextView>(chipId).setOnClickListener {
                selectedType = type
                // Update chip visuals
                typeChips.forEach { (id, _) ->
                    val chip = findViewById<TextView>(id)
                    if (id == chipId) {
                        chip.setBackgroundResource(R.drawable.bg_chip_active)
                        chip.setTextColor(Color.WHITE)
                    } else {
                        chip.setBackgroundResource(R.drawable.bg_chip_inactive)
                        chip.setTextColor(Color.parseColor("#555555"))
                    }
                }
                // Update preview
                tvPreviewIcon.text = typeIcons[type] ?: "📢"
                tvPreviewType.text = typeLabels[type] ?: "General"
            }
        }

        // Send button
        findViewById<MaterialButton>(R.id.btnSendNotification).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val message = etMessage.text.toString().trim()

            if (title.isEmpty() || message.isEmpty()) {
                Toast.makeText(this, "Please fill in both title and message!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendNotification(title, message, selectedType)
        }

        // History RecyclerView
        val rv = findViewById<RecyclerView>(R.id.rvNotificationHistory)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = NotificationHistoryAdapter(emptyList())
        rv.adapter = adapter

        loadNotificationHistory()
    }

    private fun sendNotification(title: String, message: String, type: String) {
        val btn = findViewById<MaterialButton>(R.id.btnSendNotification)
        btn.isEnabled = false
        btn.text = "Sending..."

        val notification = hashMapOf(
            "title" to title,
            "message" to message,
            "type" to type,
            "sentAt" to Timestamp.now(),
            "sentBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: "admin")
        )

        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Notification sent successfully!", Toast.LENGTH_SHORT).show()
                // Clear inputs
                findViewById<EditText>(R.id.etNotifTitle).text.clear()
                findViewById<EditText>(R.id.etNotifMessage).text.clear()
                btn.isEnabled = true
                btn.text = "🚀  Send Notification"
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Failed: ${e.message}", Toast.LENGTH_LONG).show()
                btn.isEnabled = true
                btn.text = "🚀  Send Notification"
            }
    }

    private fun loadNotificationHistory() {
        val tvEmpty = findViewById<TextView>(R.id.tvNoNotifications)
        db.collection("notifications")
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.isEmpty) {
                    tvEmpty.visibility = View.GONE
                    val notifications = snapshot.documents.map { doc ->
                        NotificationModel(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            message = doc.getString("message") ?: "",
                            type = doc.getString("type") ?: "general",
                            sentAt = doc.getTimestamp("sentAt")?.toDate()
                        )
                    }
                    adapter.updateData(notifications)
                } else {
                    tvEmpty.visibility = View.VISIBLE
                    adapter.updateData(emptyList())
                }
            }
    }

    // ─── History Adapter ────────────────────────────────────────────────────
    class NotificationHistoryAdapter(
        private var items: List<NotificationModel>
    ) : RecyclerView.Adapter<NotificationHistoryAdapter.VH>() {

        private val typeLabels = mapOf(
            "offer" to "🏷️ Offer",
            "promo_code" to "🎟️ Promo Code",
            "new_arrival" to "👟 New Arrival",
            "general" to "📢 General"
        )

        fun updateData(newItems: List<NotificationModel>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tvHistoryType)
            val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
            val tvMessage: TextView = view.findViewById(R.id.tvHistoryMessage)
            val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification_history, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvType.text = typeLabels[item.type] ?: "📢 General"
            holder.tvTitle.text = item.title
            holder.tvMessage.text = item.message
            holder.tvTime.text = item.sentAt?.let { formatTimeAgo(it) } ?: "Just now"

            // Color-code the type badge
            val badgeColor = when (item.type) {
                "offer" -> "#E53935"
                "promo_code" -> "#6A1B9A"
                "new_arrival" -> "#00897B"
                else -> "#455A64"
            }
            holder.tvType.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(badgeColor))
        }

        private fun formatTimeAgo(date: Date): String {
            val diff = System.currentTimeMillis() - date.time
            val minutes = diff / 60000
            val hours = minutes / 60
            val days = hours / 24

            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
            }
        }
    }
}
