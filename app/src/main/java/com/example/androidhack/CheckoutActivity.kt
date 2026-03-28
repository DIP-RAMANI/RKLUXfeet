package com.example.androidhack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.androidhack.models.CartItem
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject
import java.util.UUID

class CheckoutActivity : AppCompatActivity(), PaymentResultListener {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var uid: String? = null

    private lateinit var tvItemsCount: TextView
    private lateinit var tvSubtotal: TextView
    private lateinit var tvShipping: TextView
    private lateinit var tvTaxes: TextView
    private lateinit var tvTotal: TextView

    private lateinit var etFullName: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var etCity: TextInputEditText
    private lateinit var etPincode: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var rgPaymentMethod: RadioGroup

    private var cartItems = mutableListOf<CartItem>()
    private var grandTotal = 0.0

    // Temporary storage for order creation after payment
    private var pendingOrderId: String? = null
    private var pendingOrderData: HashMap<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        // Preload Razorpay
        Checkout.preload(applicationContext)

        uid = auth.currentUser?.uid

        tvItemsCount = findViewById(R.id.tvCheckoutItemsCount)
        tvSubtotal = findViewById(R.id.tvCheckoutSubtotal)
        tvShipping = findViewById(R.id.tvCheckoutShipping)
        tvTaxes = findViewById(R.id.tvCheckoutTaxes)
        tvTotal = findViewById(R.id.tvCheckoutTotal)

        etFullName = findViewById(R.id.etFullName)
        etAddress = findViewById(R.id.etAddress)
        etCity = findViewById(R.id.etCity)
        etPincode = findViewById(R.id.etPincode)
        etPhone = findViewById(R.id.etPhone)
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod)

        findViewById<ImageView>(R.id.ivBackCheckout).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnPlaceOrder).setOnClickListener {
            placeOrder()
        }

        loadCartItems()
        autoFillSavedAddress()
    }

    private fun loadCartItems() {
        if (uid == null) return

        db.collection("carts").document(uid!!)
            .collection("items")
            .get()
            .addOnSuccessListener { snapshot ->
                cartItems.clear()
                for (doc in snapshot.documents) {
                    val item = CartItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        price = doc.getString("price") ?: "0",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        quantity = doc.getLong("quantity") ?: 1L
                    )
                    cartItems.add(item)
                }
                updateTotals()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load cart", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTotals() {
        var subtotal = 0.0
        var totalItems = 0L
        for (item in cartItems) {
            val priceStr = item.price.replace("[^\\d.]".toRegex(), "")
            val priceVal = priceStr.toDoubleOrNull() ?: 0.0
            subtotal += (priceVal * item.quantity)
            totalItems += item.quantity
        }

        val taxes = subtotal * 0.05
        grandTotal = subtotal + taxes

        tvItemsCount.text = "Items ($totalItems)"
        tvSubtotal.text = "₹${subtotal.toInt()}"
        tvTaxes.text = "₹${taxes.toInt()}"
        tvTotal.text = "₹${grandTotal.toInt()}"
    }

    // Auto-fill shipping form from the user's first saved Firestore address
    private fun autoFillSavedAddress() {
        if (uid == null) return
        db.collection("users").document(uid!!)
            .collection("addresses")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull() ?: return@addOnSuccessListener
                val fullName    = doc.getString("fullName") ?: ""
                val addressLine = doc.getString("addressLine") ?: ""
                val city        = doc.getString("city") ?: ""
                val pincode     = doc.getString("pincode") ?: ""
                val phone       = doc.getString("phone") ?: ""

                if (etFullName.text.isNullOrEmpty()) etFullName.setText(fullName)
                if (etAddress.text.isNullOrEmpty())  etAddress.setText(addressLine)
                if (etCity.text.isNullOrEmpty())     etCity.setText(city)
                if (etPincode.text.isNullOrEmpty())  etPincode.setText(pincode)
                if (etPhone.text.isNullOrEmpty())    etPhone.setText(phone)
            }
    }

    private fun placeOrder() {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etFullName.text.toString().trim()
        val addressLine = etAddress.text.toString().trim()
        val city = etCity.text.toString().trim()
        val pincode = etPincode.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty() || addressLine.isEmpty() || city.isEmpty() || pincode.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all shipping details", Toast.LENGTH_SHORT).show()
            return
        }

        val paymentMethod = when (rgPaymentMethod.checkedRadioButtonId) {
            R.id.rbUPI -> "UPI"
            R.id.rbCard -> "Card"
            else -> "COD"
        }

        val orderId = UUID.randomUUID().toString()
        val itemsMap = cartItems.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "price" to it.price,
                "imageUrl" to it.imageUrl,
                "quantity" to it.quantity
            )
        }

        val orderData = hashMapOf(
            "orderId" to orderId,
            "userId" to (uid ?: "guest"),
            "customerName" to name,
            "address" to "$addressLine, $city - $pincode",
            "phone" to phone,
            "paymentMethod" to paymentMethod,
            "status" to "Pending",
            "totalAmount" to grandTotal,
            "items" to itemsMap,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        pendingOrderId = orderId
        pendingOrderData = orderData

        if (paymentMethod == "COD") {
            saveOrderToFirestore()
        } else {
            startRazorpayCheckout(name, phone, auth.currentUser?.email ?: "guest@example.com")
        }
    }

    private fun startRazorpayCheckout(name: String, phone: String, email: String) {
        val checkout = Checkout()
        // Test Key. The user can replace this with their actual key.
        checkout.setKeyID("rzp_test_SV3mBW8HNP1Ewf") 

        try {
            val options = JSONObject()
            options.put("name", "RK LUX feet")
            options.put("description", "Premium Shoe Purchase")
            options.put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png")
            options.put("theme.color", "#D1F16B") // Brand color
            options.put("currency", "INR")
            // amount in paise (multiply by 100)
            val amountInPaise = Math.round(grandTotal * 100).toString()
            options.put("amount", amountInPaise)

            val retryObj = JSONObject()
            retryObj.put("enabled", true)
            retryObj.put("max_count", 4)
            options.put("retry", retryObj)

            val prefill = JSONObject()
            prefill.put("email", email)
            prefill.put("contact", phone)
            options.put("prefill", prefill)

            checkout.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting payment: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentID: String?) {
        Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
        // Record payment ID inside order data if desired
        pendingOrderData?.put("razorpayPaymentId", razorpayPaymentID ?: "")
        pendingOrderData?.put("paymentStatus", "Paid")
        saveOrderToFirestore()
    }

    override fun onPaymentError(code: Int, response: String?) {
        Toast.makeText(this, "Payment Failed: $response", Toast.LENGTH_LONG).show()
        pendingOrderId = null
        pendingOrderData = null
    }

    private fun saveOrderToFirestore() {
        val oId = pendingOrderId ?: return
        val oData = pendingOrderData ?: return

        db.collection("orders").document(oId)
            .set(oData)
            .addOnSuccessListener {
                clearCartAndFinish(oId)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to place order. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearCartAndFinish(orderId: String) {
        if (uid != null) {
            val cartRef = db.collection("carts").document(uid!!).collection("items")
            cartRef.get().addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
            }
        }
        
        Toast.makeText(this, "Order Placed Successfully!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, OrderDetailsActivity::class.java)
        intent.putExtra("orderId", orderId)
        startActivity(intent)
        finish()
    }
}
