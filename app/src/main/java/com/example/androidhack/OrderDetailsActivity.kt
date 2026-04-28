package com.example.androidhack

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.androidhack.models.CartItem
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cloudinary.android.MediaManager

class OrderDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    // Store loaded order data for invoice generation
    private var currentOrderId      = ""
    private var currentShortId      = ""
    private var currentStatus       = ""
    private var currentAddress      = ""
    private var currentPayment      = ""
    private var currentTotal        = 0.0
    private var currentOrderDate    = ""
    private var currentItems        = listOf<CartItem>()
    private var currentPromoCode    = ""
    private var currentDiscount     = 0.0

    private lateinit var tvOrderId: TextView
    private lateinit var tvOrderDate: TextView
    private lateinit var tvOrderStatus: TextView
    private lateinit var tvOrderTotalAmount: TextView
    private lateinit var tvShippingAddress: TextView
    private lateinit var tvPaymentMethod: TextView
    private lateinit var ivPaymentMethodIcon: ImageView
    private lateinit var rvItems: RecyclerView

    // Promo UI
    private lateinit var rlOrderPromoRow: android.widget.RelativeLayout
    private lateinit var tvOrderPromoCode: TextView
    private lateinit var tvOrderPromoDiscount: TextView
    
    // Tracking Timeline UI — 5 steps
    private lateinit var ivStep1: ImageView
    private lateinit var ivStep2: ImageView
    private lateinit var ivStep3: ImageView
    private lateinit var ivStep4: ImageView
    private lateinit var ivStep5: ImageView
    private lateinit var lineStep1: View
    private lateinit var lineStep2: View
    private lateinit var lineStep3: View
    private lateinit var lineStep4: View
    private lateinit var tvStep1Title: TextView
    private lateinit var tvStep2Title: TextView
    private lateinit var tvStep3Title: TextView
    private lateinit var tvStep4Title: TextView
    private lateinit var tvStep5Title: TextView
    private lateinit var tvStep1Time: TextView
    private lateinit var tvStep2Time: TextView
    private lateinit var tvStep3Time: TextView
    private lateinit var tvStep4Time: TextView
    private lateinit var tvStep5Time: TextView
    // Tracking info
    private lateinit var cvTrackingInfo: androidx.cardview.widget.CardView
    private lateinit var tvCourierName: TextView
    private lateinit var tvTrackingNumber: TextView
    // Firestore listener
    private var orderListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val uid by lazy { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }
    // Return/Exchange UI
    private lateinit var btnReturnExchange: android.widget.Button
    private lateinit var cvReturnStatus: androidx.cardview.widget.CardView
    private lateinit var tvReturnStatusDetail: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_details)

        tvOrderId = findViewById(R.id.tvOrderId)
        tvOrderDate = findViewById(R.id.tvOrderDate)
        tvOrderStatus = findViewById(R.id.tvOrderStatus)
        tvOrderTotalAmount = findViewById(R.id.tvOrderTotalAmount)
        tvShippingAddress = findViewById(R.id.tvShippingAddress)
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod)
        ivPaymentMethodIcon = findViewById(R.id.ivPaymentMethodIcon)

        rlOrderPromoRow = findViewById(R.id.rlOrderPromoRow)
        tvOrderPromoCode = findViewById(R.id.tvOrderPromoCode)
        tvOrderPromoDiscount = findViewById(R.id.tvOrderPromoDiscount)
        
        // 5-Step Timeline
        ivStep1 = findViewById(R.id.ivStep1)
        ivStep2 = findViewById(R.id.ivStep2)
        ivStep3 = findViewById(R.id.ivStep3)
        ivStep4 = findViewById(R.id.ivStep4)
        ivStep5 = findViewById(R.id.ivStep5)
        lineStep1 = findViewById(R.id.lineStep1)
        lineStep2 = findViewById(R.id.lineStep2)
        lineStep3 = findViewById(R.id.lineStep3)
        lineStep4 = findViewById(R.id.lineStep4)
        tvStep1Title = findViewById(R.id.tvStep1Title)
        tvStep2Title = findViewById(R.id.tvStep2Title)
        tvStep3Title = findViewById(R.id.tvStep3Title)
        tvStep4Title = findViewById(R.id.tvStep4Title)
        tvStep5Title = findViewById(R.id.tvStep5Title)
        tvStep1Time = findViewById(R.id.tvStep1Time)
        tvStep2Time = findViewById(R.id.tvStep2Time)
        tvStep3Time = findViewById(R.id.tvStep3Time)
        tvStep4Time = findViewById(R.id.tvStep4Time)
        tvStep5Time = findViewById(R.id.tvStep5Time)
        cvTrackingInfo = findViewById(R.id.cvTrackingInfo)
        tvCourierName = findViewById(R.id.tvCourierName)
        tvTrackingNumber = findViewById(R.id.tvTrackingNumber)

        btnReturnExchange = findViewById(R.id.btnReturnExchange)
        cvReturnStatus = findViewById(R.id.cvReturnStatus)
        tvReturnStatusDetail = findViewById(R.id.tvReturnStatusDetail)
        
        rvItems = findViewById(R.id.rvOrderDetailsItems)
        rvItems.layoutManager = LinearLayoutManager(this)

        findViewById<ImageView>(R.id.ivBackOrderDetails).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        
        findViewById<ImageView>(R.id.ivHelpOrderDetails).setOnClickListener {
            // Open WhatsApp with pre-filled support message
            val phone = "919023382852" // Replace with your WhatsApp support number
            val message = "Hi RKLUXfeet Support! I need help with my order #$currentShortId"
            val uri = android.net.Uri.parse("https://wa.me/$phone?text=${android.net.Uri.encode(message)}")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not installed. Call us or email: support@rkluxfeet.com", Toast.LENGTH_LONG).show()
            }
        }
        
        findViewById<Button>(R.id.btnDownloadInvoice).setOnClickListener {
            if (currentOrderId.isEmpty()) {
                Toast.makeText(this, "Order data not loaded yet. Please wait.", Toast.LENGTH_SHORT).show()
            } else {
                requestStorageAndDownload()
            }
        }

        findViewById<Button>(R.id.btnEmailInvoice).setOnClickListener {
            if (currentOrderId.isEmpty()) {
                Toast.makeText(this, "Order data not loaded yet. Please wait.", Toast.LENGTH_SHORT).show()
            } else {
                emailInvoice()
            }
        }

        val orderId = intent.getStringExtra("orderId")
        val isNewOrder = intent.getBooleanExtra("isNewOrder", false)
        
        if (orderId != null) {
            loadOrderDetails(orderId)
            if (isNewOrder) {
                // Fire Success Snackbar explicitly bypassing default coloring
                val snackbar = Snackbar.make(findViewById(android.R.id.content), "🎉 Order Placed Successfully!", Snackbar.LENGTH_LONG)
                snackbar.view.setBackgroundColor(Color.parseColor("#1A1A2E")) // Dark background
                snackbar.setTextColor(Color.WHITE)
                snackbar.show()
            }
        } else {
            Toast.makeText(this, "Order ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadOrderDetails(orderId: String) {
        orderListener = db.collection("orders").document(orderId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) {
                    if (error != null) Toast.makeText(this, "Failed to load order", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val status        = doc.getString("status") ?: "Pending"
                val address       = doc.getString("address") ?: ""
                val paymentMethod = doc.getString("paymentMethod") ?: ""
                val totalAmount   = doc.getDouble("totalAmount") ?: 0.0
                val timestamp     = doc.getTimestamp("createdAt")
                val shortId       = doc.getString("shortOrderId") ?: orderId.take(10)
                val promoCode     = doc.getString("appliedPromoCode") ?: ""
                val discount      = doc.getDouble("discountAmount") ?: 0.0
                val trackingNum   = doc.getString("trackingNumber") ?: ""
                val courierName   = doc.getString("courierName") ?: ""

                currentOrderId   = orderId
                currentShortId   = shortId
                currentStatus    = status
                currentAddress   = address
                currentPayment   = paymentMethod
                currentTotal     = totalAmount
                currentPromoCode = promoCode
                currentDiscount  = discount

                tvOrderId.text = "Order #$shortId"
                tvOrderStatus.text = status
                tvShippingAddress.text = address
                tvPaymentMethod.text = paymentMethod
                tvOrderTotalAmount.text = "\u20b9${totalAmount.toInt()}"

                // Show promo row only if a promo was applied
                if (promoCode.isNotEmpty() && discount > 0) {
                    rlOrderPromoRow.visibility = View.VISIBLE
                    tvOrderPromoCode.text = promoCode
                    tvOrderPromoDiscount.text = "-\u20b9${discount.toInt()}"
                } else {
                    rlOrderPromoRow.visibility = View.GONE
                }

                // Parse statusHistory for timestamps
                val statusHistory = doc.get("statusHistory") as? List<Map<String, Any>> ?: emptyList()
                setupStatusBadgeAndTimeline(status, statusHistory)

                // Tracking info
                if (trackingNum.isNotEmpty()) {
                    cvTrackingInfo.visibility = View.VISIBLE
                    tvTrackingNumber.text = trackingNum
                    tvCourierName.text = courierName.ifEmpty { "Standard Shipping" }
                } else {
                    cvTrackingInfo.visibility = View.GONE
                }

                // Payment Icon
                if (paymentMethod.contains("UPI", true)) {
                    ivPaymentMethodIcon.setImageResource(R.drawable.ic_profile)
                } else if (paymentMethod.contains("COD", true) || paymentMethod.contains("Cash", true)) {
                    ivPaymentMethodIcon.setImageResource(R.drawable.ic_cart)
                } else {
                    ivPaymentMethodIcon.setImageResource(R.drawable.ic_card)
                }

                if (timestamp != null) {
                    val sdf = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                    val formatted = sdf.format(timestamp.toDate())
                    currentOrderDate = formatted
                    tvOrderDate.text = "Placed on $formatted"
                }

                val itemsList = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                val parsedItems = itemsList.map { itemMap ->
                    CartItem(
                        id       = itemMap["id"] as? String ?: "",
                        name     = itemMap["name"] as? String ?: "",
                        price    = itemMap["price"] as? String ?: "0",
                        imageUrl = itemMap["imageUrl"] as? String ?: "",
                        quantity = (itemMap["quantity"] as? Number)?.toLong() ?: 1L,
                        size     = itemMap["size"] as? String ?: ""
                    )
                }

                currentItems = parsedItems
                rvItems.adapter = OrderDetailAdapter(parsedItems)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        orderListener?.remove()
    }
    
    companion object {
        private const val REQUEST_WRITE_STORAGE = 101
    }

    private fun requestStorageAndDownload() {
        // On Android 10+ (Q), MediaStore doesn't need runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            generateAndDownloadInvoice()
            return
        }
        // Android 9 and below: request WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            generateAndDownloadInvoice()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                generateAndDownloadInvoice()
            } else {
                Toast.makeText(this, "Storage permission denied. Cannot save invoice.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun generateAndDownloadInvoice() {
        try {
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = pdfDoc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val boldPaint = Paint().apply { typeface = Typeface.DEFAULT_BOLD; textSize = 22f; color = Color.BLACK }
            val titlePaint = Paint().apply { typeface = Typeface.DEFAULT_BOLD; textSize = 18f; color = Color.parseColor("#7C4DFF") }
            val normalPaint = Paint().apply { textSize = 14f; color = Color.DKGRAY }
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
            val greenPaint = Paint().apply { typeface = Typeface.DEFAULT_BOLD; textSize = 16f; color = Color.parseColor("#43A047") }

            var y = 50f

            // Header
            canvas.drawText("RKLUXfeet", 40f, y, boldPaint.apply { textSize = 28f; color = Color.parseColor("#7C4DFF") })
            y += 26f
            canvas.drawText("Premium footwear for every step.", 40f, y, normalPaint)
            y += 30f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f

            // Invoice title
            canvas.drawText("INVOICE", 40f, y, titlePaint)
            y += 22f
            canvas.drawText("Order ID: #$currentShortId", 40f, y, normalPaint)
            y += 20f
            canvas.drawText("Date: $currentOrderDate", 40f, y, normalPaint)
            y += 20f
            canvas.drawText("Status: $currentStatus", 40f, y, normalPaint)
            y += 30f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f

            // Shipping & Payment
            canvas.drawText("Shipping Address", 40f, y, boldPaint.apply { textSize = 14f; color = Color.BLACK })
            y += 20f
            canvas.drawText(currentAddress, 40f, y, normalPaint)
            y += 20f
            canvas.drawText("Payment: $currentPayment", 40f, y, normalPaint)
            y += 30f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f

            // Items header
            canvas.drawText("Item", 40f, y, boldPaint.apply { textSize = 13f; color = Color.BLACK })
            canvas.drawText("Qty", 380f, y, boldPaint)
            canvas.drawText("Price", 470f, y, boldPaint)
            y += 20f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 18f

            // Items
            for (item in currentItems) {
                val itemNameWithSize = "${item.name.take(30)} (Size: ${item.size.ifEmpty { "N/A" }})"
                canvas.drawText(itemNameWithSize, 40f, y, normalPaint)
                canvas.drawText("${item.quantity}", 380f, y, normalPaint)
                canvas.drawText(item.price, 470f, y, normalPaint)
                y += 20f
            }

            y += 10f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f

            // Promo discount line (only if applied)
            if (currentPromoCode.isNotEmpty() && currentDiscount > 0) {
                canvas.drawText("Promo Code: $currentPromoCode", 40f, y, normalPaint)
                canvas.drawText("-\u20b9${currentDiscount.toInt()}", 470f, y, greenPaint.apply { textSize = 14f })
                y += 22f
            }

            // Grand Total
            canvas.drawText("Total Amount:", 40f, y, boldPaint.apply { textSize = 16f })
            canvas.drawText("\u20b9${currentTotal.toInt()}", 470f, y, greenPaint.apply { textSize = 16f })
            y += 40f
            canvas.drawText("Thank you for shopping with RKLUXfeet!", 40f, y, normalPaint)

            pdfDoc.finishPage(page)

            // Save to Downloads using short ID
            val fileName = "Invoice_$currentShortId.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { pdfDoc.writeTo(it) }
                    pdfDoc.close()
                    Toast.makeText(this, "Invoice saved to Downloads!", Toast.LENGTH_LONG).show()
                    openPdf(uri)
                }
            } else {
                // Android 9 and below
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                pdfDoc.writeTo(FileOutputStream(file))
                pdfDoc.close()
                val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
                Toast.makeText(this, "Invoice saved to Downloads!", Toast.LENGTH_LONG).show()
                openPdf(uri)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to generate invoice: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun emailInvoice() {
        val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
        if (userEmail.isEmpty()) {
            Toast.makeText(this, "No email linked to your account.", Toast.LENGTH_SHORT).show()
            return
        }

        val btnEmail = findViewById<Button>(R.id.btnEmailInvoice)
        btnEmail.isEnabled = false
        btnEmail.text = "Sending..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Generate PDF exactly as before
                val pdfDoc = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas: android.graphics.Canvas = page.canvas

                val boldPaint   = android.graphics.Paint().apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 22f; color = Color.BLACK }
                val titlePaint  = android.graphics.Paint().apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 18f; color = Color.parseColor("#7C4DFF") }
                val normalPaint = android.graphics.Paint().apply { textSize = 14f; color = Color.DKGRAY }
                val linePaint   = android.graphics.Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
                val greenPaint  = android.graphics.Paint().apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 16f; color = Color.parseColor("#43A047") }

                var y = 50f
                canvas.drawText("RKLUXfeet", 40f, y, boldPaint.apply { textSize = 28f; color = Color.parseColor("#7C4DFF") })
                y += 26f; canvas.drawText("Premium footwear for every step.", 40f, y, normalPaint)
                y += 30f; canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 24f; canvas.drawText("INVOICE", 40f, y, titlePaint)
                y += 22f; canvas.drawText("Order ID: #$currentShortId", 40f, y, normalPaint)
                y += 20f; canvas.drawText("Date: $currentOrderDate", 40f, y, normalPaint)
                y += 20f; canvas.drawText("Status: $currentStatus", 40f, y, normalPaint)
                y += 30f; canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 24f; canvas.drawText("Shipping Address", 40f, y, boldPaint.apply { textSize = 14f; color = Color.BLACK })
                y += 20f; canvas.drawText(currentAddress, 40f, y, normalPaint)
                y += 20f; canvas.drawText("Payment: $currentPayment", 40f, y, normalPaint)
                y += 30f; canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 24f
                canvas.drawText("Item", 40f, y, boldPaint.apply { textSize = 13f })
                canvas.drawText("Qty", 380f, y, boldPaint); canvas.drawText("Price", 470f, y, boldPaint)
                y += 20f; canvas.drawLine(40f, y, 555f, y, linePaint); y += 18f
                for (item in currentItems) {
                    canvas.drawText("${item.name.take(30)} (${item.size.ifEmpty { "N/A" }})", 40f, y, normalPaint)
                    canvas.drawText("${item.quantity}", 380f, y, normalPaint)
                    canvas.drawText(item.price, 470f, y, normalPaint)
                    y += 20f
                }
                y += 10f; canvas.drawLine(40f, y, 555f, y, linePaint); y += 24f
                if (currentPromoCode.isNotEmpty() && currentDiscount > 0) {
                    canvas.drawText("Promo: $currentPromoCode", 40f, y, normalPaint)
                    canvas.drawText("-\u20b9${currentDiscount.toInt()}", 470f, y, greenPaint.apply { textSize = 14f })
                    y += 22f
                }
                canvas.drawText("Total Amount:", 40f, y, boldPaint.apply { textSize = 16f })
                canvas.drawText("\u20b9${currentTotal.toInt()}", 470f, y, greenPaint.apply { textSize = 16f })
                y += 40f; canvas.drawText("Thank you for shopping with RKLUXfeet!", 40f, y, normalPaint)

                pdfDoc.finishPage(page)

                val tempFile = File(cacheDir, "Invoice_$currentShortId.pdf")
                pdfDoc.writeTo(java.io.FileOutputStream(tempFile))
                pdfDoc.close()

                // 2. Upload to Cloudinary (using raw resource type for PDF)
                val result = MediaManager.get().cloudinary.uploader().unsignedUpload(
                    tempFile.absolutePath,
                    "android_uploads",
                    com.cloudinary.utils.ObjectUtils.asMap("resource_type", "raw")
                )
                
                val publicUrl = result["secure_url"] as String

                // 3. Queue email in Firestore mail collection (for Firebase Trigger Email Extension)
                val mailDoc = hashMapOf(
                    "to" to userEmail,
                    "message" to hashMapOf(
                        "subject" to "Your RKLUXfeet Invoice - Order #$currentShortId",
                        "html" to "Hi there,<br><br>Here is your invoice for Order #$currentShortId.<br><br><b><a href=\"$publicUrl\">Click here to download your Invoice PDF</a></b><br><br>Thank you for shopping with RKLUXfeet! 👟<br><br>— RKLUXfeet Team"
                    )
                )

                db.collection("mail").add(mailDoc).addOnSuccessListener {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(this@OrderDetailsActivity, "✅ Invoice emailed successfully!", Toast.LENGTH_LONG).show()
                        btnEmail.text = "📧 Email Invoice"
                        btnEmail.isEnabled = true
                    }
                }.addOnFailureListener {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(this@OrderDetailsActivity, "Failed to queue email", Toast.LENGTH_SHORT).show()
                        btnEmail.text = "📧 Email Invoice"
                        btnEmail.isEnabled = true
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OrderDetailsActivity, "Error sending email: ${e.message}", Toast.LENGTH_LONG).show()
                    btnEmail.text = "📧 Email Invoice"
                    btnEmail.isEnabled = true
                }
            }
        }
    }

    // Status Badge & 5-Step Timeline
    private fun setupStatusBadgeAndTimeline(status: String, statusHistory: List<Map<String, Any>>) {
        val statusOrder = listOf("Pending", "Confirmed", "Shipped", "Out for Delivery", "Delivered")
        val s = status.lowercase(Locale.getDefault())

        // Determine active step index
        val activeIndex = when {
            s.contains("cancel") -> -1 // Cancelled
            s.contains("deliv") && !s.contains("out") -> 4
            s.contains("out") -> 3
            s.contains("ship") || s.contains("dispatch") -> 2
            s.contains("confirm") -> 1
            else -> 0 // Pending
        }

        // Badge color
        val badgeColor = when {
            s.contains("cancel") -> "#757575"
            activeIndex >= 4     -> "#4CAF50"
            activeIndex >= 2     -> "#FF9800"
            activeIndex >= 1     -> "#7C4DFF"
            else                 -> "#FFB300"
        }
        tvOrderStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(badgeColor))

        // Show/hide Rate button and Return button
        val btnRate = findViewById<android.widget.Button>(R.id.btnRateProduct)
        val isDelivered = activeIndex >= 4
        btnRate.visibility = if (isDelivered) View.VISIBLE else View.GONE

        // Check if return request already exists for this order
        if (isDelivered && currentOrderId.isNotEmpty()) {
            db.collection("returnRequests")
                .whereEqualTo("orderId", currentOrderId)
                .get()
                .addOnSuccessListener { snap ->
                    if (!snap.isEmpty) {
                        val req = snap.documents[0]
                        val reqStatus = req.getString("status") ?: "Pending"
                        val reqType = req.getString("type") ?: "Return"
                        val reqReason = req.getString("reason") ?: ""
                        cvReturnStatus.visibility = View.VISIBLE
                        btnReturnExchange.visibility = View.GONE
                        tvReturnStatusDetail.text = "Type: $reqType  |  Reason: $reqReason\nStatus: $reqStatus"
                    } else {
                        cvReturnStatus.visibility = View.GONE
                        btnReturnExchange.visibility = View.VISIBLE
                        btnReturnExchange.setOnClickListener { showReturnDialog() }
                    }
                }
        } else if (!isDelivered) {
            btnReturnExchange.visibility = View.GONE
            cvReturnStatus.visibility = View.GONE
        }

        // Helper: find timestamp for a given status in statusHistory
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        fun findTimestamp(statusName: String): String {
            val entry = statusHistory.find { (it["status"] as? String)?.equals(statusName, true) == true }
            val ts = entry?.get("timestamp")
            return when (ts) {
                is com.google.firebase.Timestamp -> sdf.format(ts.toDate())
                is Map<*, *> -> {
                    val seconds = (ts["seconds"] as? Number)?.toLong() ?: return ""
                    sdf.format(java.util.Date(seconds * 1000))
                }
                else -> ""
            }
        }

        // Step views arrays
        val icons = listOf(ivStep1, ivStep2, ivStep3, ivStep4, ivStep5)
        val lines = listOf(lineStep1, lineStep2, lineStep3, lineStep4) // 4 lines between 5 steps
        val titles = listOf(tvStep1Title, tvStep2Title, tvStep3Title, tvStep4Title, tvStep5Title)
        val times = listOf(tvStep1Time, tvStep2Time, tvStep3Time, tvStep4Time, tvStep5Time)

        for (i in 0..4) {
            val timestamp = findTimestamp(statusOrder[i])
            times[i].text = timestamp

            when {
                i < activeIndex -> {
                    // Completed step
                    icons[i].setBackgroundResource(R.drawable.bg_step_completed)
                    icons[i].imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    titles[i].setTextColor(Color.parseColor("#4CAF50"))
                    titles[i].setTypeface(null, android.graphics.Typeface.BOLD)
                    if (i < 4) lines[i].setBackgroundColor(Color.parseColor("#4CAF50"))
                }
                i == activeIndex -> {
                    // Current/active step
                    icons[i].setBackgroundResource(R.drawable.bg_step_active)
                    icons[i].imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    titles[i].setTextColor(Color.parseColor("#7C4DFF"))
                    titles[i].setTypeface(null, android.graphics.Typeface.BOLD)
                    if (i < 4) lines[i].setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
                else -> {
                    // Future step
                    icons[i].setBackgroundResource(R.drawable.bg_step_inactive)
                    icons[i].imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
                    titles[i].setTextColor(Color.parseColor("#BDBDBD"))
                    titles[i].setTypeface(null, android.graphics.Typeface.NORMAL)
                    if (i < 4) lines[i].setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
            }
        }

        // Cancelled special case: grey out everything
        if (activeIndex == -1) {
            for (i in 0..4) {
                icons[i].setBackgroundResource(R.drawable.bg_step_inactive)
                icons[i].imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
                titles[i].setTextColor(Color.parseColor("#BDBDBD"))
                if (i < 4) lines[i].setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
        }
    }

    private fun showReturnDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_return_request, null)
        dialog.setContentView(view)
        (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        var selectedType = "Return"
        var selectedReason = ""

        val btnTypeReturn = view.findViewById<android.widget.TextView>(R.id.btnTypeReturn)
        val btnTypeExchange = view.findViewById<android.widget.TextView>(R.id.btnTypeExchange)

        val reasonButtons = listOf(
            view.findViewById<android.widget.TextView>(R.id.reasonWrongSize) to "Wrong Size",
            view.findViewById<android.widget.TextView>(R.id.reasonDefective) to "Defective",
            view.findViewById<android.widget.TextView>(R.id.reasonNotAsDesc) to "Not as Described",
            view.findViewById<android.widget.TextView>(R.id.reasonChanged) to "Changed Mind",
            view.findViewById<android.widget.TextView>(R.id.reasonOther) to "Other"
        )

        fun updateTypeUI() {
            listOf(btnTypeReturn, btnTypeExchange).forEach {
                it.setBackgroundResource(R.drawable.bg_size_chip_unselected)
                it.setTextColor(android.graphics.Color.parseColor("#424242"))
            }
            val active = if (selectedType == "Return") btnTypeReturn else btnTypeExchange
            active.setBackgroundResource(R.drawable.bg_size_chip_selected)
            active.setTextColor(android.graphics.Color.parseColor("#7C4DFF"))
        }

        fun updateReasonUI() {
            reasonButtons.forEach { (btn, label) ->
                if (label == selectedReason) {
                    btn.setBackgroundResource(R.drawable.bg_size_chip_selected)
                    btn.setTextColor(android.graphics.Color.parseColor("#7C4DFF"))
                } else {
                    btn.setBackgroundResource(R.drawable.bg_size_chip_unselected)
                    btn.setTextColor(android.graphics.Color.parseColor("#424242"))
                }
            }
        }

        updateTypeUI()

        btnTypeReturn.setOnClickListener { selectedType = "Return"; updateTypeUI() }
        btnTypeExchange.setOnClickListener { selectedType = "Exchange"; updateTypeUI() }
        reasonButtons.forEach { (btn, label) ->
            btn.setOnClickListener { selectedReason = label; updateReasonUI() }
        }

        view.findViewById<android.widget.Button>(R.id.btnSubmitReturn).setOnClickListener {
            if (selectedReason.isEmpty()) {
                Toast.makeText(this, "Please select a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val notes = view.findViewById<android.widget.EditText>(R.id.etReturnNotes).text.toString().trim()
            val request = hashMapOf(
                "orderId"   to currentOrderId,
                "shortOrderId" to currentShortId,
                "userId"    to (uid ?: ""),
                "type"      to selectedType,
                "reason"    to selectedReason,
                "notes"     to notes,
                "status"    to "Pending",
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            db.collection("returnRequests").add(request)
                .addOnSuccessListener {
                    dialog.dismiss()
                    cvReturnStatus.visibility = View.VISIBLE
                    btnReturnExchange.visibility = View.GONE
                    tvReturnStatusDetail.text = "Type: $selectedType  |  Reason: $selectedReason\nStatus: Pending"
                    Toast.makeText(this, "✅ Request submitted! We'll contact you within 24 hrs.", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to submit. Try again.", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    class OrderDetailAdapter(private val items: List<CartItem>) : RecyclerView.Adapter<OrderDetailAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvOrderDetailItemName)
            val tvQty: TextView = view.findViewById(R.id.tvOrderDetailItemQuantity)
            val tvSize: TextView = view.findViewById(R.id.tvOrderDetailItemSize)
            val ivItem: ImageView = view.findViewById(R.id.ivOrderDetailItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order_detail, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvQty.text = "Quantity: ${item.quantity}"
            holder.tvSize.text = "Size: ${item.size.ifEmpty { "N/A" }}"
            
            Glide.with(holder.itemView.context)
                .load(item.imageUrl.optimizeCloudinaryUrl())
                .centerCrop()
                .placeholder(R.drawable.shoes017)
                .into(holder.ivItem)
        }

        override fun getItemCount() = items.size
    }
}
