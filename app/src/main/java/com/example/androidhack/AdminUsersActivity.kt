package com.example.androidhack

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
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class AdminUsersActivity : AppCompatActivity() {

    data class UserModel(
        val uid: String = "",
        val name: String = "",
        val email: String = "",
        val orderCount: Int = 0,
        val isActive: Boolean = true
    )

    private val db = FirebaseFirestore.getInstance()
    private var allUsers = listOf<UserModel>()
    private lateinit var adapter: UserAdapter
    private var currentFilter = "All"
    private var currentSearch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_users)

        findViewById<ImageView>(R.id.ivUsersBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvUsers)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(emptyList(), db)
        rv.adapter = adapter

        // Search Bar logic
        findViewById<EditText>(R.id.etUsersSearch).addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearch = s.toString().trim()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter Chip logic
        setupFilterChip(R.id.chipFilterAll, "All")
        setupFilterChip(R.id.chipFilterActive, "Active")
        setupFilterChip(R.id.chipFilterBlocked, "Blocked")

        // Load users from Firestore
        db.collection("users").get().addOnSuccessListener { snapshot ->
            allUsers = snapshot.documents.mapIndexed { index, doc ->
                UserModel(
                    uid = doc.id,
                    name = doc.getString("name") ?: "Unknown",
                    email = doc.getString("email") ?: "No email",
                    // Mocking blocked status for demo ui logic
                    isActive = index % 5 != 0
                )
            }
            applyFilters()
        }
    }

    private fun setupFilterChip(id: Int, filterStatus: String) {
        val allChips = listOf(R.id.chipFilterAll, R.id.chipFilterActive, R.id.chipFilterBlocked)
        findViewById<TextView>(id).setOnClickListener {
            currentFilter = filterStatus
            allChips.forEach { chipId ->
                val chip = findViewById<TextView>(chipId)
                if (chipId == id) {
                    chip.setBackgroundResource(R.drawable.bg_chip_active)
                    chip.setTextColor(android.graphics.Color.WHITE)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_inactive)
                    chip.setTextColor(android.graphics.Color.BLACK)
                }
            }
            applyFilters()
        }
    }

    private fun applyFilters() {
        var filtered = allUsers
        
        // Search text mapping
        if (currentSearch.isNotEmpty()) {
            filtered = filtered.filter { 
                it.name.contains(currentSearch, ignoreCase = true) || 
                it.email.contains(currentSearch, ignoreCase = true) 
            }
        }

        // Chip Status Filtering
        if (currentFilter == "Active") {
            filtered = filtered.filter { it.isActive }
        } else if (currentFilter == "Blocked") {
            filtered = filtered.filter { !it.isActive }
        }

        findViewById<TextView>(R.id.tvUserCount).text = "${filtered.size} users"
        adapter.updateData(filtered)
    }

    class UserAdapter(
        private var users: List<UserModel>,
        private val db: FirebaseFirestore
    ) : RecyclerView.Adapter<UserAdapter.UserVH>() {

        fun updateData(newUsers: List<UserModel>) {
            users = newUsers
            notifyDataSetChanged()
        }

        class UserVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvInitial: TextView    = view.findViewById(R.id.tvUserInitial)
            val tvName: TextView       = view.findViewById(R.id.tvUserName)
            val tvEmail: TextView      = view.findViewById(R.id.tvUserEmail)
            val tvOrderCount: TextView = view.findViewById(R.id.tvUserOrderCount)
            val cvAvatar: CardView     = view.findViewById(R.id.cvUserAvatar)
            val tvStatus: TextView     = view.findViewById(R.id.tvUserStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            UserVH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_user, parent, false))

        override fun getItemCount() = users.size

        override fun onBindViewHolder(holder: UserVH, position: Int) {
            val user = users[position]
            holder.tvInitial.text    = user.name.firstOrNull()?.uppercase() ?: "?"
            holder.tvName.text       = user.name
            holder.tvEmail.text      = user.email

            // Avatar random pastel color
            val pastelColors = arrayOf(
                android.graphics.Color.parseColor("#4CAF50"), // Green
                android.graphics.Color.parseColor("#F44336"), // Red
                android.graphics.Color.parseColor("#2196F3"), // Blue
                android.graphics.Color.parseColor("#FF9800"), // Orange
                android.graphics.Color.parseColor("#9C27B0")  // Purple
            )
            // Use ID hash to keep colors consistent
            val colorIndex = Math.abs(user.uid.hashCode()) % pastelColors.size
            holder.cvAvatar.setCardBackgroundColor(pastelColors[colorIndex])

            // Mock Status Badge
            if (user.isActive) {
                holder.tvStatus.text = "Active"
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                holder.tvStatus.text = "Blocked"
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))
            }

            // Fetch true order count dynamically
            db.collection("orders").whereEqualTo("userId", user.uid).get()
                .addOnSuccessListener { holder.tvOrderCount.text = "${it.size()} orders" }
        }
    }
}
