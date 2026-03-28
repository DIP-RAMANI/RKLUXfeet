package com.example.androidhack

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PersonalInformationActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid get() = auth.currentUser?.uid

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_information)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)

        findViewById<ImageView>(R.id.ivBackInfo).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSaveChanges).setOnClickListener {
            saveChanges()
        }

        loadUserData()
    }

    private fun loadUserData() {
        if (uid == null) return
        
        db.collection("users").document(uid!!).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val name = doc.getString("name") ?: ""
                val email = doc.getString("email") ?: auth.currentUser?.email ?: ""
                val phone = doc.getString("phone") ?: ""

                etName.setText(name)
                etEmail.setText(email)
                etPhone.setText(phone)
            } else {
                // Fallback to Auth email if document doesn't exist yet
                etEmail.setText(auth.currentUser?.email ?: "")
            }
        }
    }

    private fun saveChanges() {
        if (uid == null) {
            Toast.makeText(this, "Not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Name and Email are required", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "email" to email,
            "phone" to phone
        )

        db.collection("users").document(uid!!)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Changes saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
