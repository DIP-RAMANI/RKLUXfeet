package com.example.androidhack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class PaymentMethodsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_methods)

        findViewById<ImageView>(R.id.ivBackPayment).setOnClickListener {
            finish()
        }

        val rvPaymentMethods = findViewById<RecyclerView>(R.id.rvPaymentMethods)
        val dummyPayments = listOf(
            PaymentItem("Visa", "Ending in 4242"),
            PaymentItem("Mastercard", "Ending in 1234"),
            PaymentItem("PayPal", "")
        )
        
        rvPaymentMethods.adapter = PaymentAdapter(dummyPayments)
    }

    data class PaymentItem(val title: String, val detail: String)

    class PaymentAdapter(private val payments: List<PaymentItem>) : RecyclerView.Adapter<PaymentAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvPaymentTitle)
            val tvDetail: TextView = view.findViewById(R.id.tvPaymentDetail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_method, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val payment = payments[position]
            holder.tvTitle.text = payment.title
            if (payment.detail.isNotEmpty()) {
                holder.tvDetail.text = payment.detail
                holder.tvDetail.visibility = View.VISIBLE
            } else {
                holder.tvDetail.visibility = View.GONE
            }
        }

        override fun getItemCount() = payments.size
    }
}
