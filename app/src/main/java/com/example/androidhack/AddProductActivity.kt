package com.example.androidhack

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class AddProductActivity : AppCompatActivity() {

    private lateinit var etProductName: EditText
    private lateinit var etProductPrice: EditText
    private lateinit var etProductImageUrl: EditText
    private lateinit var btnSaveProduct: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_product)

        etProductName = findViewById(R.id.etProductName)
        etProductPrice = findViewById(R.id.etProductPrice)
        etProductImageUrl = findViewById(R.id.etProductImageUrl)
        btnSaveProduct = findViewById(R.id.btnSaveProduct)

        btnSaveProduct.setOnClickListener {
            val name = etProductName.text.toString().trim()
            val price = etProductPrice.text.toString().trim()
            var imageUrl = etProductImageUrl.text.toString().trim()

            if (name.isEmpty() || price.isEmpty()) {
                Toast.makeText(this, "Please enter a product name and price!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Provide a default placeholder image if the user leaves the URL blank
            if (imageUrl.isEmpty()) {
                imageUrl = "https://res.cloudinary.com/dx7dfwcfl/image/upload/v1711586000/default_shoe.png" 
            }

            saveProductToFirebase(name, price, imageUrl)
        }
    }

    private fun saveProductToFirebase(name: String, price: String, imageUrl: String) {
        val database = FirebaseDatabase.getInstance().getReference("products")
        val productId = database.push().key ?: return

        val shoe = HomeActivity.Shoe(productId, name, price, imageUrl)

        database.child(productId).setValue(shoe)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Product Added Successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Close screen automatically
                } else {
                    Toast.makeText(this, "Failed to save to Firebase", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
