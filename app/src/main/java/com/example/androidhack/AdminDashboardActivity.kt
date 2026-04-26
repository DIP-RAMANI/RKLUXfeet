package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminDashboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        findViewById<ImageView>(R.id.ivAdminBack).setOnClickListener { finish() }

        loadStats()

        findViewById<Button>(R.id.btnManageUsers).setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageOrders).setOnClickListener {
            startActivity(Intent(this, AdminOrdersActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageProducts).setOnClickListener {
            startActivity(Intent(this, AdminProductsActivity::class.java))
        }
        findViewById<Button>(R.id.btnManagePromoCodes).setOnClickListener {
            startActivity(Intent(this, AdminPromoCodesActivity::class.java))
        }
    }

    private fun loadStats() {
        val tvUsers    = findViewById<TextView>(R.id.tvTotalUsers)
        val tvOrders   = findViewById<TextView>(R.id.tvTotalOrders)
        val tvPending  = findViewById<TextView>(R.id.tvPendingOrders)
        val tvProducts = findViewById<TextView>(R.id.tvTotalProducts)

        db.collection("users").get().addOnSuccessListener { tvUsers.text = it.size().toString() }
        db.collection("orders").get().addOnSuccessListener { tvOrders.text = it.size().toString() }
        db.collection("orders").whereEqualTo("status", "Pending").get()
            .addOnSuccessListener { tvPending.text = it.size().toString() }
        db.collection("products").get().addOnSuccessListener { tvProducts.text = it.size().toString() }
    }

    override fun onResume() {
        super.onResume()
        loadStats() // Refresh stats when returning from sub-screens
    }
}
