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
    
    // Tracking Timeline UI
    private lateinit var lineTracking1: View
    private lateinit var lineTracking2: View
    private lateinit var ivTrackingShipped: ImageView
    private lateinit var ivTrackingDelivered: ImageView

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
        
        lineTracking1 = findViewById(R.id.lineTracking1)
        lineTracking2 = findViewById(R.id.lineTracking2)
        ivTrackingShipped = findViewById(R.id.ivTrackingShipped)
        ivTrackingDelivered = findViewById(R.id.ivTrackingDelivered)
        
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
        db.collection("orders").document(orderId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val status        = doc.getString("status") ?: "Pending"
                    val address       = doc.getString("address") ?: ""
                    val paymentMethod = doc.getString("paymentMethod") ?: ""
                    val totalAmount   = doc.getDouble("totalAmount") ?: 0.0
                    val timestamp     = doc.getTimestamp("createdAt")
                    val shortId       = doc.getString("shortOrderId") ?: orderId.take(10)
                    val promoCode     = doc.getString("appliedPromoCode") ?: ""
                    val discount      = doc.getDouble("discountAmount") ?: 0.0

                    currentOrderId   = orderId
                    currentShortId   = shortId
                    currentStatus    = status
                    currentAddress   = address
                    currentPayment   = paymentMethod
                    currentTotal     = totalAmount
                    currentPromoCode = promoCode
                    currentDiscount  = discount

                    // Show short, readable order ID
                    tvOrderId.text = "Order #$shortId"
                    tvOrderStatus.text = status
                    tvShippingAddress.text = address
                    tvPaymentMethod.text = paymentMethod
                    tvOrderTotalAmount.text = "\u20b9${totalAmount.toInt()}"

                    // Show promo row only if a promo was applied
                    if (promoCode.isNotEmpty() && discount > 0) {
                        rlOrderPromoRow.visibility = android.view.View.VISIBLE
                        tvOrderPromoCode.text = promoCode
                        tvOrderPromoDiscount.text = "-\u20b9${discount.toInt()}"
                    } else {
                        rlOrderPromoRow.visibility = android.view.View.GONE
                    }

                    // Format Badge & Timeline Logic
                    setupStatusBadgeAndTimeline(status)
                    
                    // Format Payment Icon dynamically targeting vectors present
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
                } else {
                    Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load order", Toast.LENGTH_SHORT).show()
            }
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

    // Status Badge & Event Path Mapping
    private fun setupStatusBadgeAndTimeline(status: String) {
        val s = status.lowercase(Locale.getDefault())
        when {
            s.contains("deliv") || s.contains("complet") -> {
                // Delivered (Green Badge)
                tvOrderStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                // Timeline Shipped active
                lineTracking1.setBackgroundColor(Color.parseColor("#FFB300"))
                ivTrackingShipped.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                // Timeline Delivered active
                lineTracking2.setBackgroundColor(Color.parseColor("#4CAF50"))
                ivTrackingDelivered.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            }
            s.contains("ship") || s.contains("dispatch") -> {
                // Shipped (Orange Badge)
                tvOrderStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9800"))
                // Timeline Shipped active
                lineTracking1.setBackgroundColor(Color.parseColor("#FFB300"))
                ivTrackingShipped.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9800"))
                // Timeline Delivered inactive
                lineTracking2.setBackgroundColor(Color.parseColor("#EEEEEE"))
                ivTrackingDelivered.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            }
            else -> {
                // Pending (Yellow Badge)
                tvOrderStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFB300"))
                // Timeline all inactive
                lineTracking1.setBackgroundColor(Color.parseColor("#EEEEEE"))
                ivTrackingShipped.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
                lineTracking2.setBackgroundColor(Color.parseColor("#EEEEEE"))
                ivTrackingDelivered.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            }
        }
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
