package com.example.androidhack

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cloudinary.android.MediaManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminAddProductActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var selectedImageUri: Uri? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val ivPreview = findViewById<ImageView>(R.id.ivAdminProductPreview)
            ivPreview.visibility = android.view.View.VISIBLE
            ivPreview.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_add_product)

        findViewById<ImageView>(R.id.ivAddProductBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            imagePicker.launch("image/*")
        }

        findViewById<Button>(R.id.btnSaveAdminProduct).setOnClickListener {
            val name  = findViewById<EditText>(R.id.etAdminProductName).text.toString().trim()
            val price = findViewById<EditText>(R.id.etAdminProductPrice).text.toString().trim()
            val desc  = findViewById<EditText>(R.id.etAdminProductDesc).text.toString().trim()
            val specs = findViewById<EditText>(R.id.etAdminProductSpecs).text.toString().trim()
            val urlField = findViewById<EditText>(R.id.etAdminImageUrl).text.toString().trim()

            if (name.isEmpty() || price.isEmpty()) {
                Toast.makeText(this, "Name and Price are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedImageUri != null) {
                uploadImageThenSave(name, price, desc, specs, selectedImageUri!!)
            } else if (urlField.isNotEmpty()) {
                saveToFirestore(name, price, desc, specs, urlField)
            } else {
                // Save with default placeholder image
                saveToFirestore(name, price, desc, specs, "https://via.placeholder.com/400x300.png?text=${name.replace(" ", "+")}")
            }
        }
    }

    private fun uploadImageThenSave(name: String, price: String, desc: String, specs: String, uri: Uri) {
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_LONG).show()

        val tempFile = java.io.File.createTempFile("product", ".jpg", cacheDir)
        val input = contentResolver.openInputStream(uri)
        val output = java.io.FileOutputStream(tempFile)
        input?.copyTo(output)
        input?.close()
        output.close()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = MediaManager.get().cloudinary.uploader().unsignedUpload(
                    tempFile.absolutePath,
                    "android_uploads",
                    com.cloudinary.utils.ObjectUtils.emptyMap()
                )
                tempFile.delete()
                val imageUrl = result["secure_url"] as String? ?: ""
                withContext(Dispatchers.Main) {
                    saveToFirestore(name, price, desc, specs, imageUrl)
                }
            } catch (e: Exception) {
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminAddProductActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveToFirestore(name: String, price: String, desc: String, specs: String, imageUrl: String) {
        val product = hashMapOf(
            "name"     to name,
            "price"    to price,
            "description" to desc,
            "specifications" to specs,
            "imageUrl" to imageUrl
        )
        db.collection("products").add(product)
            .addOnSuccessListener {
                Toast.makeText(this, "Product saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
