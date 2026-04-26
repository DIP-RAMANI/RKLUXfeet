package com.example.androidhack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidhack.models.PromoCode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AdminPromoCodesActivity : AppCompatActivity() {

    private lateinit var rvPromoCodes: RecyclerView
    private lateinit var fabAddPromo: FloatingActionButton
    private lateinit var ivBackPromos: ImageView

    private val db = FirebaseFirestore.getInstance()
    private val promoList = mutableListOf<PromoCode>()
    private lateinit var promoAdapter: PromoCodeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_promo_codes)

        rvPromoCodes = findViewById(R.id.rvPromoCodes)
        fabAddPromo = findViewById(R.id.fabAddPromo)
        ivBackPromos = findViewById(R.id.ivBackPromos)

        ivBackPromos.setOnClickListener { finish() }

        rvPromoCodes.layoutManager = LinearLayoutManager(this)
        promoAdapter = PromoCodeAdapter(promoList) { promoId ->
            deletePromoCode(promoId)
        }
        rvPromoCodes.adapter = promoAdapter

        fabAddPromo.setOnClickListener {
            showAddPromoDialog()
        }

        loadPromoCodes()
    }

    private fun loadPromoCodes() {
        db.collection("promocodes").get()
            .addOnSuccessListener { snapshot ->
                promoList.clear()
                for (doc in snapshot.documents) {
                    val code = doc.getString("code") ?: ""
                    val type = doc.getString("type") ?: "Percentage"
                    val value = doc.getDouble("value") ?: 0.0
                    val isActive = doc.getBoolean("isActive") ?: true
                    promoList.add(PromoCode(doc.id, code, type, value, isActive))
                }
                promoAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load promo codes", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddPromoDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_promo, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val etCode = dialogView.findViewById<TextInputEditText>(R.id.etNewPromoCode)
        val etValue = dialogView.findViewById<TextInputEditText>(R.id.etNewPromoValue)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgPromoType)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAddPromo)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelPromo)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val codeTxt = etCode.text.toString().trim()
            val valTxt = etValue.text.toString().trim()
            val type = if (rgType.checkedRadioButtonId == R.id.rbTypePercentage) "Percentage" else "Flat"

            if (codeTxt.isEmpty() || valTxt.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val value = valTxt.toDoubleOrNull()
            if (value == null || value <= 0) {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newId = UUID.randomUUID().toString()
            val promoData = hashMapOf(
                "code" to codeTxt,
                "type" to type,
                "value" to value,
                "isActive" to true
            )

            db.collection("promocodes").document(newId).set(promoData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Promo Code Added", Toast.LENGTH_SHORT).show()
                    loadPromoCodes()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to add code", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun deletePromoCode(promoId: String) {
        db.collection("promocodes").document(promoId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Promo Code Deleted", Toast.LENGTH_SHORT).show()
                loadPromoCodes()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
    }

    // Inner Adapter
    inner class PromoCodeAdapter(
        private val list: List<PromoCode>,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.Adapter<PromoCodeAdapter.PromoViewHolder>() {

        inner class PromoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvCode: TextView = view.findViewById(R.id.tvPromoCodeText)
            val tvValue: TextView = view.findViewById(R.id.tvPromoValueText)
            val ivDelete: ImageView = view.findViewById(R.id.ivDeletePromo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromoViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_promo_code, parent, false)
            return PromoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PromoViewHolder, position: Int) {
            val promo = list[position]
            holder.tvCode.text = promo.code
            
            val valStr = if (promo.type == "Percentage") {
                "${promo.value.toInt()}% OFF"
            } else {
                "₹${promo.value.toInt()} OFF"
            }
            holder.tvValue.text = valStr

            holder.ivDelete.setOnClickListener {
                onDeleteClick(promo.id)
            }
        }

        override fun getItemCount() = list.size
    }
}
