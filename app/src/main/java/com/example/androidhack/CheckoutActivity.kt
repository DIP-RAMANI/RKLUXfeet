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

    private lateinit var rvAvailablePromos: androidx.recyclerview.widget.RecyclerView
    private lateinit var etPromoCode: TextInputEditText
    private lateinit var btnApplyPromo: Button
    private lateinit var rlDiscount: android.widget.RelativeLayout
    private lateinit var tvCheckoutDiscount: TextView

    private var availablePromos = mutableListOf<com.example.androidhack.models.PromoCode>()
    private var appliedPromo: com.example.androidhack.models.PromoCode? = null
    private var actualDiscountAmount: Double = 0.0

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

        rvAvailablePromos = findViewById(R.id.rvAvailablePromos)
        etPromoCode = findViewById(R.id.etPromoCode)
        btnApplyPromo = findViewById(R.id.btnApplyPromo)
        rlDiscount = findViewById(R.id.rlDiscount)
        tvCheckoutDiscount = findViewById(R.id.tvCheckoutDiscount)

        rvAvailablePromos.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)

        btnApplyPromo.setOnClickListener {
            val code = etPromoCode.text.toString().trim().uppercase()
            checkAndApplyPromo(code)
        }

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
        loadActivePromos()
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
                        quantity = doc.getLong("quantity") ?: 1L,
                        size = doc.getString("size") ?: ""
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

        actualDiscountAmount = 0.0
        val promo = appliedPromo
        if (promo != null) {
            if (promo.type == "Percentage") {
                actualDiscountAmount = (subtotal * promo.value) / 100.0
            } else {
                actualDiscountAmount = promo.value
            }
            if (actualDiscountAmount > subtotal) {
                actualDiscountAmount = subtotal
            }
            rlDiscount.visibility = android.view.View.VISIBLE
            tvCheckoutDiscount.text = "-₹${actualDiscountAmount.toInt()}"
        } else {
            rlDiscount.visibility = android.view.View.GONE
        }

        val afterDiscount = subtotal - actualDiscountAmount
        val taxes = afterDiscount * 0.05
        grandTotal = afterDiscount + taxes

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

        pendingOrderId = null
        pendingOrderData = null

        // Count existing orders to generate a sequential short ID like RKLUX001
        db.collection("orders").get().addOnSuccessListener { snapshot ->
            val nextNum   = snapshot.size() + 1
            val shortId   = "RKLUX" + nextNum.toString().padStart(3, '0')
            val orderId   = java.util.UUID.randomUUID().toString()

            val itemsMap = cartItems.map {
                mapOf(
                    "id"       to it.id,
                    "name"     to it.name,
                    "price"    to it.price,
                    "imageUrl" to it.imageUrl,
                    "quantity" to it.quantity,
                    "size"     to it.size
                )
            }

            val orderData = hashMapOf(
                "orderId"          to orderId,
                "shortOrderId"     to shortId,
                "userId"           to (uid ?: "guest"),
                "customerName"     to name,
                "address"          to "$addressLine, $city - $pincode",
                "phone"            to phone,
                "paymentMethod"    to paymentMethod,
                "status"           to "Pending",
                "totalAmount"      to grandTotal,
                "appliedPromoCode" to (appliedPromo?.code ?: ""),
                "discountAmount"   to actualDiscountAmount,
                "items"            to itemsMap,
                "createdAt"        to com.google.firebase.Timestamp.now()
            )

            pendingOrderId   = orderId
            pendingOrderData = orderData

            if (paymentMethod == "COD") {
                saveOrderToFirestore()
            } else {
                startRazorpayCheckout(name, phone, auth.currentUser?.email ?: "guest@example.com")
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to place order. Try again.", Toast.LENGTH_SHORT).show()
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

    private fun loadActivePromos() {
        db.collection("promocodes")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snapshot ->
                availablePromos.clear()
                for (doc in snapshot.documents) {
                    val code = doc.getString("code") ?: ""
                    val type = doc.getString("type") ?: "Percentage"
                    val value = doc.getDouble("value") ?: 0.0
                    val isActive = doc.getBoolean("isActive") ?: true
                    availablePromos.add(com.example.androidhack.models.PromoCode(doc.id, code, type, value, isActive))
                }
                rvAvailablePromos.adapter = AvailablePromoAdapter(availablePromos) { clickedCode ->
                    etPromoCode.setText(clickedCode)
                }
            }
    }

    private fun checkAndApplyPromo(codeInput: String) {
        if (codeInput.isEmpty()) {
            appliedPromo = null
            updateTotals()
            Toast.makeText(this, "Promo code removed", Toast.LENGTH_SHORT).show()
            return
        }

        val match = availablePromos.find { it.code.trim().equals(codeInput.trim(), ignoreCase = true) }
        if (match != null) {
            appliedPromo = match
            updateTotals()
            Toast.makeText(this, "Promo code '${match.code}' applied!", Toast.LENGTH_SHORT).show()
        } else {
            appliedPromo = null
            updateTotals()
            Toast.makeText(this, "Invalid promo code", Toast.LENGTH_SHORT).show()
        }
    }

    // Inner Adapter
    inner class AvailablePromoAdapter(
        private val list: List<com.example.androidhack.models.PromoCode>,
        private val onClick: (String) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<AvailablePromoAdapter.PromoViewHolder>() {

        inner class PromoViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val tvCode: TextView = view.findViewById(R.id.tvChipPromoCode)
            val tvValue: TextView = view.findViewById(R.id.tvChipPromoValue)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PromoViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_available_promo, parent, false)
            return PromoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PromoViewHolder, position: Int) {
            val promo = list[position]
            holder.tvCode.text = promo.code
            if (promo.type == "Percentage") {
                holder.tvValue.text = " (${promo.value.toInt()}% OFF)"
            } else {
                holder.tvValue.text = " (₹${promo.value.toInt()} OFF)"
            }
            holder.itemView.setOnClickListener {
                onClick(promo.code)
            }
        }

        override fun getItemCount() = list.size
    }
}
