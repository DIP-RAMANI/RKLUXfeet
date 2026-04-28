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

    private var editProductId: String? = null
    private var existingImageUrls: List<String> = emptyList()

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
        
        editProductId = intent.getStringExtra("productId")
        if (editProductId != null) {
            findViewById<Button>(R.id.btnSaveAdminProduct).text = "✅  Update Product"
            loadProductData(editProductId!!)
        }

        // Save button
        findViewById<Button>(R.id.btnSaveAdminProduct).setOnClickListener {
            val name     = findViewById<EditText>(R.id.etAdminProductName).text.toString().trim()
            val price    = findViewById<EditText>(R.id.etAdminProductPrice).text.toString().trim()
            val story    = findViewById<EditText>(R.id.etAdminProductStory).text.toString().trim()
            val features = findViewById<EditText>(R.id.etAdminProductFeatures).text.toString().trim()
            val details  = findViewById<EditText>(R.id.etAdminProductDetails).text.toString().trim()
            val brand    = findViewById<EditText>(R.id.etAdminProductBrand).text.toString().trim()
            val discount = findViewById<EditText>(R.id.etAdminProductDiscount).text.toString().trim().toIntOrNull() ?: 0
            val inStock  = findViewById<android.widget.Switch>(R.id.swInStock).isChecked

            if (name.isEmpty() || price.isEmpty()) {
                Toast.makeText(this, "Name and Price are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pickedCount = selectedUris.count { it != null }
            
            // If editing and no new images selected, just update data
            if (editProductId != null && pickedCount == 0 && existingImageUrls.isNotEmpty()) {
                saveToFirestore(name, price, story, features, details, brand, discount, inStock, existingImageUrls)
                return@setOnClickListener
            }
            
            if (pickedCount < 2 && editProductId == null) {
                Toast.makeText(this, "Please select at least 2 images!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Upload all selected images and collect URLs
            val urisToUpload = selectedUris.filterNotNull()
            uploadAllImages(name, price, story, features, details, brand, discount, inStock, urisToUpload)
        }
    }
    
    private fun loadProductData(id: String) {
        db.collection("products").document(id).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                findViewById<EditText>(R.id.etAdminProductName).setText(doc.getString("name") ?: "")
                findViewById<EditText>(R.id.etAdminProductPrice).setText(doc.getString("price") ?: "")
                findViewById<EditText>(R.id.etAdminProductStory).setText(doc.getString("productStory") ?: "")
                findViewById<EditText>(R.id.etAdminProductFeatures).setText(doc.getString("features") ?: "")
                findViewById<EditText>(R.id.etAdminProductDetails).setText(doc.getString("details") ?: "")
                findViewById<EditText>(R.id.etAdminProductBrand).setText(doc.getString("brand") ?: "")
                
                val discount = doc.getLong("discountPercent") ?: 0L
                if (discount > 0) {
                    findViewById<EditText>(R.id.etAdminProductDiscount).setText(discount.toString())
                }
                
                val inStock = doc.getBoolean("inStock") ?: true
                findViewById<android.widget.Switch>(R.id.swInStock).isChecked = inStock

                val urls = (doc.get("imageUrls") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                existingImageUrls = urls
                
                // Show existing images using Glide
                urls.take(4).forEachIndexed { index, url ->
                    previews[index].visibility = View.VISIBLE
                    com.bumptech.glide.Glide.with(this).load(url).into(previews[index])
                    // Hide remove button for existing images to keep UI simple
                    removeBtns[index].visibility = View.GONE 
                }
            }
        }
    }

    private fun showPreview(slot: Int, uri: Uri) {
        previews[slot].visibility = View.VISIBLE
        previews[slot].setImageURI(uri)
        removeBtns[slot].visibility = View.VISIBLE
    }

    private fun uploadAllImages(
        name: String, price: String, story: String, features: String,
        details: String, brand: String, discountPercent: Int, inStock: Boolean,
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
                    saveToFirestore(name, price, story, features, details, brand, discountPercent, inStock, uploadedUrls)
                }
            }
        }
    }

    private fun saveToFirestore(
        name: String, price: String, story: String, features: String,
        details: String, brand: String, discountPercent: Int, inStock: Boolean,
        imageUrls: List<String>
    ) {
        val product = hashMapOf(
            "name"            to name,
            "price"           to price,
            "productStory"    to story,
            "features"        to features,
            "details"         to details,
            // Backward compatibility: combine story + features into old description field
            "description"     to story,
            "specifications"  to features,
            "brand"           to brand.lowercase(),
            "discountPercent" to discountPercent,
            "inStock"         to inStock,
            "imageUrl"        to (imageUrls.firstOrNull() ?: ""),   // backward-compat
            "imageUrls"       to imageUrls,
            "createdAt"       to com.google.firebase.Timestamp.now()
        )
        
        if (editProductId != null) {
            db.collection("products").document(editProductId!!).set(product)
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Product updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
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
}
