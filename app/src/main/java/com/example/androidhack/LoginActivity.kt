package com.example.androidhack

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvForgotPassword: TextView
    private lateinit var layoutGoogleLogin: LinearLayout
    private lateinit var btnLogin: Button
    private lateinit var tvNoAccount: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // By bypassing the RESULT_OK check, we force Google to hand us their internal error message (ApiException)
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null && account.idToken != null) {
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                Toast.makeText(this, "Wait! Expected Google Token was missing.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            // EXTREMELY IMPORTANT: This surfaces exactly WHY Google rejected the login
            val errorCode = e.statusCode
            val errorMessage = e.message
            android.util.Log.e("GoogleSignIn", "Failed with code: $errorCode, msg: $errorMessage", e)
            Toast.makeText(this, "Google Error Code: $errorCode. Please read my message!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnBack = findViewById(R.id.btnBack)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        layoutGoogleLogin = findViewById(R.id.layoutGoogleLogin)
        btnLogin = findViewById(R.id.btnLogin)
        tvNoAccount = findViewById(R.id.tvNoAccount)

        btnBack.setOnClickListener { finish() }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        layoutGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            when {
                email.isEmpty() -> showToast("Please enter your email")
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    showToast("Please enter a valid email")
                password.isEmpty() -> showToast("Please enter your password")
                else -> {
                    // Standard Firebase Email/Password Auth
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid
                                if (uid != null) MyFirebaseMessagingService.saveFcmToken(uid)
                                showToast("Login successful!")
                                val intent = Intent(this, HomeActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                showToast("Login failed: ${task.exception?.message}")
                            }
                        }
                }
            }
        }

        tvNoAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                val userMap = mutableMapOf<String, Any>(
                                    "name" to (user.displayName ?: "No Name"),
                                    "email" to (user.email ?: ""),
                                    "profileImageUrl" to (user.photoUrl?.toString() ?: "")
                                )
                                if (!doc.exists()) {
                                    userMap["role"] = "user"
                                }
                                db.collection("users").document(user.uid)
                                    .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                    .addOnCompleteListener {
                                        MyFirebaseMessagingService.saveFcmToken(user.uid)
                                        showToast("Google Login successful!")
                                        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    }
                            }
                            .addOnFailureListener {
                                showToast("Failed to fetch user data")
                            }
                    }
                } else {
                    showToast("Firebase Google Auth failed: ${task.exception?.message}")
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
