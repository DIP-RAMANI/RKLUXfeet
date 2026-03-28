package com.example.androidhack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddressActivity : AppCompatActivity() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid  get() = auth.currentUser?.uid

    private lateinit var rvAddresses: RecyclerView
    private val addressList = mutableListOf<AddressItem>()
    private lateinit var adapter: AddressAdapter

    data class AddressItem(
        val id: String = "",
        val label: String = "",       // e.g. "Home", "Work"
        val fullName: String = "",
        val addressLine: String = "",
        val city: String = "",
        val pincode: String = "",
        val phone: String = ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)

        rvAddresses = findViewById(R.id.rvAddresses)
        adapter = AddressAdapter(addressList) { item -> confirmDelete(item) }
        rvAddresses.adapter = adapter

        findViewById<ImageView>(R.id.ivBackAddress).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnAddAddress).setOnClickListener {
            showAddAddressDialog()
        }

        loadAddresses()
    }

    private fun loadAddresses() {
        if (uid == null) return
        db.collection("users").document(uid!!)
            .collection("addresses")
            .get()
            .addOnSuccessListener { snapshot ->
                addressList.clear()
                for (doc in snapshot.documents) {
                    addressList.add(
                        AddressItem(
                            id          = doc.id,
                            label       = doc.getString("label") ?: "",
                            fullName    = doc.getString("fullName") ?: "",
                            addressLine = doc.getString("addressLine") ?: "",
                            city        = doc.getString("city") ?: "",
                            pincode     = doc.getString("pincode") ?: "",
                            phone       = doc.getString("phone") ?: ""
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun showAddAddressDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_address, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add New Address")
            .setView(dialogView)
            .setPositiveButton("Save", null) // override below
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label       = dialogView.findViewById<TextInputEditText>(R.id.etAddressLabel).text.toString().trim()
                val fullName    = dialogView.findViewById<TextInputEditText>(R.id.etAddressFullName).text.toString().trim()
                val addressLine = dialogView.findViewById<TextInputEditText>(R.id.etAddressLine).text.toString().trim()
                val city        = dialogView.findViewById<TextInputEditText>(R.id.etAddressCity).text.toString().trim()
                val pincode     = dialogView.findViewById<TextInputEditText>(R.id.etAddressPincode).text.toString().trim()
                val phone       = dialogView.findViewById<TextInputEditText>(R.id.etAddressPhone).text.toString().trim()

                if (label.isEmpty() || fullName.isEmpty() || addressLine.isEmpty() || city.isEmpty() || pincode.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                saveAddress(label, fullName, addressLine, city, pincode, phone)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun saveAddress(label: String, fullName: String, addressLine: String, city: String, pincode: String, phone: String) {
        if (uid == null) return
        val data = hashMapOf(
            "label"       to label,
            "fullName"    to fullName,
            "addressLine" to addressLine,
            "city"        to city,
            "pincode"     to pincode,
            "phone"       to phone
        )
        db.collection("users").document(uid!!)
            .collection("addresses")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Address saved!", Toast.LENGTH_SHORT).show()
                loadAddresses()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save address", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDelete(item: AddressItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Address")
            .setMessage("Remove \"${item.label}\" address?")
            .setPositiveButton("Delete") { _, _ ->
                if (uid == null) return@setPositiveButton
                db.collection("users").document(uid!!)
                    .collection("addresses").document(item.id)
                    .delete()
                    .addOnSuccessListener { loadAddresses() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class AddressAdapter(
        private val items: List<AddressItem>,
        private val onDelete: (AddressItem) -> Unit
    ) : RecyclerView.Adapter<AddressAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle:  TextView  = view.findViewById(R.id.tvAddressTitle)
            val tvDetail: TextView  = view.findViewById(R.id.tvAddressDetail)
            val ivIcon:   ImageView = view.findViewById(R.id.ivAddressIcon)
            val btnDel:   ImageView = view.findViewById(R.id.ivAddressDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_address, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text  = item.label
            holder.tvDetail.text = "${item.fullName}\n${item.addressLine}, ${item.city} - ${item.pincode}\n📞 ${item.phone}"
            holder.ivIcon.setImageResource(
                if (item.label.equals("Home", ignoreCase = true)) android.R.drawable.ic_menu_myplaces
                else android.R.drawable.ic_menu_compass
            )
            holder.btnDel.setOnClickListener { onDelete(item) }
        }
    }
}
