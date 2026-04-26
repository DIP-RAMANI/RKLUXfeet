package com.example.androidhack

import android.net.Uri
import android.os.Bundle
import android.view.View
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

    // Up to 4 image URIs
    private val selectedUris = arrayOfNulls<Uri>(4)

    // Track which slot is being picked (0-indexed)
    private var activeSlot = 0

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUris[activeSlot] = uri
            showPreview(activeSlot, uri)
        }
    }

    // All slots: preview views + pick buttons + remove buttons
    private lateinit var previews: Array<ImageView>
    private lateinit var pickBtns: Array<Button>
    private lateinit var removeBtns: Array<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_add_product)

        findViewById<ImageView>(R.id.ivAddProductBack).setOnClickListener { finish() }

        previews = arrayOf(
            findViewById(R.id.ivPreview1),
            findViewById(R.id.ivPreview2),
            findViewById(R.id.ivPreview3),
            findViewById(R.id.ivPreview4)
        )
        pickBtns = arrayOf(
            findViewById(R.id.btnPickImage1),
            findViewById(R.id.btnPickImage2),
            findViewById(R.id.btnPickImage3),
            findViewById(R.id.btnPickImage4)
        )
        removeBtns = arrayOf(
            findViewById(R.id.btnRemoveImage1),
            findViewById(R.id.btnRemoveImage2),
            findViewById(R.id.btnRemoveImage3),
            findViewById(R.id.btnRemoveImage4)
        )

        // Wire pick & remove buttons for each slot
        for (i in 0..3) {
            val slot = i
            pickBtns[i].setOnClickListener {
                activeSlot = slot
                imagePicker.launch("image/*")
            }
            removeBtns[i].setOnClickListener {
                selectedUris[slot] = null
                previews[slot].visibility = View.GONE
                removeBtns[slot].visibility = View.GONE
            }
        }

        // Save button
        findViewById<Button>(R.id.btnSaveAdminProduct).setOnClickListener {
            val name     = findViewById<EditText>(R.id.etAdminProductName).text.toString().trim()
            val price    = findViewById<EditText>(R.id.etAdminProductPrice).text.toString().trim()
            val desc     = findViewById<EditText>(R.id.etAdminProductDesc).text.toString().trim()
            val specs    = findViewById<EditText>(R.id.etAdminProductSpecs).text.toString().trim()
            val brand    = findViewById<EditText>(R.id.etAdminProductBrand).text.toString().trim()
            val discount = findViewById<EditText>(R.id.etAdminProductDiscount).text.toString().trim().toIntOrNull() ?: 0

            if (name.isEmpty() || price.isEmpty()) {
                Toast.makeText(this, "Name and Price are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pickedCount = selectedUris.count { it != null }
            if (pickedCount < 2) {
                Toast.makeText(this, "Please select at least 2 images!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Upload all selected images and collect URLs
            val urisToUpload = selectedUris.filterNotNull()
            uploadAllImages(name, price, desc, specs, brand, discount, urisToUpload)
        }
    }

    private fun showPreview(slot: Int, uri: Uri) {
        previews[slot].visibility = View.VISIBLE
        previews[slot].setImageURI(uri)
        removeBtns[slot].visibility = View.VISIBLE
    }

    private fun uploadAllImages(
        name: String, price: String, desc: String, specs: String,
        brand: String, discountPercent: Int,
        uris: List<Uri>
    ) {
        Toast.makeText(this, "Uploading ${uris.size} image(s)...", Toast.LENGTH_LONG).show()
        val saveBtn = findViewById<Button>(R.id.btnSaveAdminProduct)
        saveBtn.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            val uploadedUrls = mutableListOf<String>()
            var errorMessage: String? = null

            for (uri in uris) {
                try {
                    val tempFile = java.io.File.createTempFile("product", ".jpg", cacheDir)
                    val input = contentResolver.openInputStream(uri)
                    val output = java.io.FileOutputStream(tempFile)
                    input?.copyTo(output)
                    input?.close()
                    output.close()

                    val result = MediaManager.get().cloudinary.uploader().unsignedUpload(
                        tempFile.absolutePath,
                        "android_uploads",
                        com.cloudinary.utils.ObjectUtils.emptyMap()
                    )
                    tempFile.delete()
                    val url = result["secure_url"] as String? ?: ""
                    if (url.isNotEmpty()) uploadedUrls.add(url)
                } catch (e: Exception) {
                    errorMessage = e.message
                    break
                }
            }

            withContext(Dispatchers.Main) {
                saveBtn.isEnabled = true
                if (errorMessage != null) {
                    Toast.makeText(this@AdminAddProductActivity, "Upload failed: $errorMessage", Toast.LENGTH_LONG).show()
                } else {
                    saveToFirestore(name, price, desc, specs, brand, discountPercent, uploadedUrls)
                }
            }
        }
    }

    private fun saveToFirestore(
        name: String, price: String, desc: String, specs: String,
        brand: String, discountPercent: Int,
        imageUrls: List<String>
    ) {
        val product = hashMapOf(
            "name"            to name,
            "price"           to price,
            "description"     to desc,
            "specifications"  to specs,
            "brand"           to brand.lowercase(),
            "discountPercent" to discountPercent,
            "imageUrl"        to (imageUrls.firstOrNull() ?: ""),   // backward-compat
            "imageUrls"       to imageUrls,
            "createdAt"       to com.google.firebase.Timestamp.now()
        )
        db.collection("products").add(product)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Product saved with ${imageUrls.size} image(s)!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
