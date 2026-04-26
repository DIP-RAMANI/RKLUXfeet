package com.example.androidhack

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PaymentMethodsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_methods)

        findViewById<ImageView>(R.id.ivBackPayment).setOnClickListener {
            finish()
        }

        // Hide the RecyclerView and show an informative empty state instead
        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPaymentMethods)
        rv.visibility = View.GONE

        val tvLabel = findViewById<TextView>(R.id.tvSavedPaymentMethods)
        tvLabel.text = "Payment Options"

        // "Add Payment Method" button informs users how to pay
        findViewById<android.widget.Button>(R.id.btnAddPaymentMethod).setOnClickListener {
            Toast.makeText(
                this,
                "Choose UPI, Card, or Cash on Delivery at checkout when placing your order.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
