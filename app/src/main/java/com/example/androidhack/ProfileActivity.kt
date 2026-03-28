package com.example.androidhack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val uid get() = auth.currentUser?.uid

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var ivProfilePic: ImageView

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            ivProfilePic.setImageURI(uri)
            uploadProfileImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvName = findViewById(R.id.tvProfileName)
        tvEmail = findViewById(R.id.tvProfileEmail)
        ivProfilePic = findViewById(R.id.ivProfilePicture)

        findViewById<TextView>(R.id.btnChangeProfilePicture).setOnClickListener {
            imagePicker.launch("image/*")
        }

        val bottomNav = findViewById<com.google.android.material.tabs.TabLayout>(R.id.bottomNavigation)
        val llAddProduct = findViewById<View>(R.id.llAddProduct)

        // Hide Admin Panel by default until role is verified from Firestore
        llAddProduct.visibility = View.GONE

        llAddProduct.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }

        findViewById<View>(R.id.llPersonalInfo).setOnClickListener {
            startActivity(Intent(this, PersonalInformationActivity::class.java))
        }

        findViewById<View>(R.id.llAddress).setOnClickListener {
            startActivity(Intent(this, AddressActivity::class.java))
        }

        findViewById<View>(R.id.llOrderHistory).setOnClickListener {
            startActivity(Intent(this, OrderHistoryActivity::class.java))
        }

        findViewById<View>(R.id.llAppSettings).setOnClickListener {
            // Open Android system App Settings for this app
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.llContactSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:deeprramani@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "RKLUXfeet Support Request")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.llSignOut).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Setup Bottom Nav
        bottomNav.getTabAt(3)?.select()
        bottomNav.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                if (tab?.position == 3) return
                val intent = when (tab?.position) {
                    0 -> Intent(this@ProfileActivity, HomeActivity::class.java)
                    1 -> Intent(this@ProfileActivity, StoreActivity::class.java)
                    2 -> Intent(this@ProfileActivity, WishlistActivity::class.java)
                    else -> null
                }
                intent?.let {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startActivity(it)
                        overridePendingTransition(0, 0)
                        finish()
                    }, 150)
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        if (uid == null) return
        db.collection("users").document(uid!!).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val name = doc.getString("name") ?: auth.currentUser?.displayName ?: "No Name"
                val email = doc.getString("email") ?: auth.currentUser?.email ?: ""
                val profileImageUrl = doc.getString("profileImageUrl") ?: ""
                val role = doc.getString("role") ?: "user"

                tvName.text = name
                tvEmail.text = email

                if (role == "admin") {
                    findViewById<View>(R.id.llAddProduct).visibility = View.VISIBLE
                }

                if (profileImageUrl.isNotEmpty()) {
                    Glide.with(this).load(profileImageUrl).centerCrop().into(ivProfilePic)
                }
            } else {
                tvName.text = auth.currentUser?.displayName ?: "No Name"
                tvEmail.text = auth.currentUser?.email ?: "No Email"
            }
        }
    }

    private fun uploadProfileImage(uri: Uri) {
        if (uid == null) return

        Toast.makeText(this, "Uploading Profile Picture...", Toast.LENGTH_LONG).show()

        val tempFile = java.io.File.createTempFile("profile", ".jpg", cacheDir)
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
                    saveProfileImageUrl(imageUrl)
                }
            } catch (e: Exception) {
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveProfileImageUrl(url: String) {
        if (uid == null) return
        db.collection("users").document(uid!!)
            .update("profileImageUrl", url)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update profile picture in database.", Toast.LENGTH_SHORT).show()
            }
    }
}
