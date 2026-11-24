package com.example.androidhack

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.androidhack.models.ProductFields
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

class StylistActivity : AppCompatActivity() {

    data class ShoeProduct(
        val id: String = "",
        val name: String = "",
        val price: String = "",
        val finalPriceNum: Double = 0.0,
        val imageUrl: String = "",
        val imageUrls: List<String> = emptyList(),
        val description: String = "",
        val specifications: String = "",
        val productStory: String = "",
        val features: String = "",
        val details: String = "",
        val brand: String = "",
        val shoeType: String = "",
        val discountPercent: Int = 0,
        val inStock: Boolean = true,
        val colors: List<String> = emptyList(),
        val occasions: List<String> = emptyList(),
        val sizes: List<String> = emptyList(),
        val avgRating: Double = 0.0
    )

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val products: List<ShoeProduct> = emptyList()
    )

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvTypingIndicator: TextView
    private lateinit var hsvSuggestionChips: HorizontalScrollView
    private lateinit var llSuggestionChips: LinearLayout
    private lateinit var adapter: ChatAdapter

    private val messages = mutableListOf<ChatMessage>()
    // Tracks conversation history as OpenAI-compatible message JSONObjects
    private val conversationMessages = mutableListOf<JSONObject>()

    private val systemInstructionText =
        "You are RKLUX Stylist, the shopping assistant of RKLUXfeet, an online shoe store. " +
        "Reply in the same language the customer writes in (English, Hindi or Gujarati). " +
        "Keep replies to 1 to 3 short sentences. " +
        "Ask at most one question in the whole conversation. " +
        "If the customer asks to see shoes without giving any type, budget or size (for example 'show me shoes'), call search_products with no filters right away and show the results. Do not ask a question first. " +
        "If the customer already gave a type like running, do not ask again, call search_products immediately. " +
        "If they did not give a size or budget, show products anyway and optionally ask once. " +
        "Recommend only products returned by the tool, never invent products, prices or sizes. " +
        "Mention by name the products being shown, in the same order, with the priceText of each. " +
        "Prices are in Indian rupees. Use priceText exactly as given. Never use £ or $, never change or convert prices. " +
        "Never tell the customer to browse the website. " +
        "If nothing matches, say so and suggest the closest option, for example a higher budget or another color."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stylist)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator)
        hsvSuggestionChips = findViewById(R.id.hsvSuggestionChips)
        llSuggestionChips = findViewById(R.id.llSuggestionChips)

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.layoutManager = layoutManager
        adapter = ChatAdapter(messages) { shoe ->
            openProductDetails(shoe)
        }
        rvMessages.adapter = adapter

        // Setup Quick-question suggestion chips
        setupSuggestionChips()

        // Initial welcome message
        addMessage(
            "Hello! I am your RKLUX Stylist ✨ Looking for running shoes, casual sneakers, or formal footwear? Tell me what you need!",
            isUser = false
        )

        updateChipsVisibility()

        btnSend.setOnClickListener {
            val userText = etMessage.text.toString().trim()
            if (userText.isNotEmpty()) {
                handleSendText(userText)
            }
        }
    }

    private fun setupSuggestionChips() {
        val chipQueries = listOf(
            "Running shoes under ₹4000",
            "Shoes for a wedding",
            "Casual sneakers",
            "Best shoes for gym",
            "Formal shoes under ₹5000",
            "Show me today's offers"
        )

        llSuggestionChips.removeAllViews()
        val marginEndPx = (8 * resources.displayMetrics.density).toInt()
        val padHPx = (14 * resources.displayMetrics.density).toInt()
        val heightPx = (36 * resources.displayMetrics.density).toInt()

        for (query in chipQueries) {
            val chip = TextView(this).apply {
                text = query
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#7C4DFF"))
                setBackgroundResource(R.drawable.bg_cat_chip_selected)
                gravity = Gravity.CENTER
                setPadding(padHPx, 0, padHPx, 0)
                isClickable = true
                isFocusable = true

                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = ContextCompat.getDrawable(this@StylistActivity, outValue.resourceId)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    heightPx
                ).apply {
                    marginEnd = marginEndPx
                }

                setOnClickListener {
                    handleSendText(query)
                }
            }
            llSuggestionChips.addView(chip)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = currentFocus ?: etMessage
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun updateChipsVisibility() {
        // Show chips only when chat is empty (only initial greeting is visible)
        val isChatEmpty = messages.size <= 1
        hsvSuggestionChips.visibility = if (isChatEmpty) View.VISIBLE else View.GONE
    }

    private fun handleSendText(text: String) {
        val userText = text.trim()
        if (userText.isEmpty()) return

        hideKeyboard()
        etMessage.text.clear()
        addMessage(userText, isUser = true)
        updateChipsVisibility()

        conversationMessages.add(JSONObject().apply {
            put("role", "user")
            put("content", userText)
        })

        setLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            val responseResult = callAiWithRetry()
            withContext(Dispatchers.Main) {
                setLoading(false)
                val replyText = responseResult.first
                if (replyText.isNotBlank() && !replyText.equals("null", ignoreCase = true)) {
                    addMessage(replyText, isUser = false, products = responseResult.second)
                }
                updateChipsVisibility()
            }
        }
    }

    private fun openProductDetails(shoe: ShoeProduct) {
        val intent = Intent(this, ProductDetailsActivity::class.java).apply {
            putExtra("productId",     shoe.id)
            putExtra("productName",   shoe.name)
            putExtra("productPrice",  shoe.price.ifEmpty { formatIndianRupees(shoe.finalPriceNum) })
            putExtra("productImage",  shoe.imageUrl)
            putStringArrayListExtra("productImageUrls", ArrayList(shoe.imageUrls.ifEmpty { if (shoe.imageUrl.isNotEmpty()) listOf(shoe.imageUrl) else emptyList() }))
            putExtra("productDesc",   shoe.description)
            putExtra("productSpecs",  shoe.specifications)
            putExtra("productStory",    shoe.productStory)
            putExtra("productFeatures", shoe.features)
            putExtra("productDetails",  shoe.details)
        }
        startActivity(intent)
    }

    private fun addMessage(text: String, isUser: Boolean, products: List<ShoeProduct> = emptyList()) {
        val cleanText = text.trim()
        // Never add "null" or empty message for assistant
        if (!isUser && (cleanText.isEmpty() || cleanText.equals("null", ignoreCase = true))) {
            return
        }

        messages.add(ChatMessage(cleanText, isUser, products))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
    }

    private fun setLoading(isLoading: Boolean) {
        tvTypingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !isLoading
    }

    private suspend fun callAiWithRetry(): Pair<String, List<ShoeProduct>> {
        val maxRetries = 2
        var attempt = 0

        while (attempt <= maxRetries) {
            try {
                Log.d("StylistAI", "Sending request to AI (attempt ${attempt + 1})...")
                val result = processChatTurn()
                if (result != null) {
                    return result
                }
            } catch (e: Exception) {
                Log.e("StylistAI", "AI request failed on attempt ${attempt + 1}", e)
            }

            attempt++
            if (attempt <= maxRetries) {
                Log.d("StylistAI", "Waiting 2 seconds before retry...")
                delay(2000)
            }
        }

        return Pair("The stylist is busy, please try again in a moment.", emptyList())
    }

    private fun safeContent(obj: JSONObject?): String {
        if (obj == null || obj.isNull("content")) return ""
        val str = obj.optString("content", "").trim()
        return if (str.equals("null", ignoreCase = true)) "" else str
    }

    private suspend fun processChatTurn(): Pair<String, List<ShoeProduct>>? {
        val apiKey = BuildConfig.OPENROUTER_API_KEY
        if (apiKey.isEmpty()) {
            return Pair("API key not configured.", emptyList())
        }

        // First API call with tools enabled
        val firstResponseJson = postOpenRouter(conversationMessages, withTools = true) ?: return null
        val choices = firstResponseJson.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null

        val choice = choices.getJSONObject(0)
        val messageObj = choice.optJSONObject("message") ?: return null

        val toolCalls = messageObj.optJSONArray("tool_calls")
        if (toolCalls != null && toolCalls.length() > 0) {
            val toolCall = toolCalls.getJSONObject(0)
            val toolCallId = toolCall.optString("id", "call_search_products")
            val functionObj = toolCall.optJSONObject("function")
            val functionName = functionObj?.optString("name", "")
            val argsStr = functionObj?.optString("arguments", "{}") ?: "{}"

            if (functionName == "search_products") {
                // 1. Run search in Kotlin using Firestore
                val (toolResultJson, matchedProducts) = executeSearchProducts(argsStr)

                // 2. Add assistant's tool_call message to conversation (do not display in UI!)
                val assistantToolCallMsg = JSONObject().apply {
                    put("role", "assistant")
                    put("content", JSONObject.NULL)
                    put("tool_calls", toolCalls)
                }
                conversationMessages.add(assistantToolCallMsg)

                // 3. Add tool response message to conversation
                val toolResponseMsg = JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", toolCallId)
                    put("content", toolResultJson)
                }
                conversationMessages.add(toolResponseMsg)

                // 4. Send follow-up request to get final reply
                val secondResponseJson = postOpenRouter(conversationMessages, withTools = false) ?: return null
                val secondChoices = secondResponseJson.optJSONArray("choices") ?: return null
                if (secondChoices.length() == 0) return null

                val secondMessageObj = secondChoices.getJSONObject(0).optJSONObject("message")
                var finalContent = safeContent(secondMessageObj)

                if (finalContent.isBlank()) {
                    finalContent = if (matchedProducts.isNotEmpty()) {
                        "Here are our recommended shoes for you:"
                    } else {
                        "I couldn't find exact matches in our catalog for that request. Would you like to check other sizes, colors, or styles?"
                    }
                }

                // Save final assistant message in history
                conversationMessages.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", finalContent)
                })

                // Show only cards for products returned by the tool (max 3)
                val productCards = matchedProducts.take(3)
                return Pair(finalContent, productCards)
            }
        }

        // No tool called, direct conversational reply
        var directContent = safeContent(messageObj)
        if (directContent.isBlank()) {
            directContent = "How can I help you find the perfect shoes today?"
        }

        conversationMessages.add(JSONObject().apply {
            put("role", "assistant")
            put("content", directContent)
        })

        return Pair(directContent, emptyList())
    }

    private fun postOpenRouter(messagesList: List<JSONObject>, withTools: Boolean): JSONObject? {
        val apiKey = BuildConfig.OPENROUTER_API_KEY
        val url = URL("https://openrouter.ai/api/v1/chat/completions")

        val rootJson = JSONObject().apply {
            put("model", "google/gemini-2.5-flash")
            put("max_tokens", 512)
            put("temperature", 0.7)

            val fullMessages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstructionText)
                })
                for (m in messagesList) {
                    put(m)
                }
            }
            put("messages", fullMessages)

            if (withTools) {
                put("tools", getToolsJson())
            }
        }

        val payloadBytes = rootJson.toString().toByteArray(Charsets.UTF_8)

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("HTTP-Referer", "https://rkluxfeet.com")
            setRequestProperty("X-Title", "RKLUXfeet Stylist")
            connectTimeout = 30000
            readTimeout = 90000
            setFixedLengthStreamingMode(payloadBytes.size)
            doOutput = true
        }

        conn.outputStream.use { it.write(payloadBytes) }

        val responseCode = conn.responseCode
        Log.d("StylistAI", "OpenRouter HTTP response code: $responseCode")

        if (responseCode in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(responseText)
        } else {
            val errorStream = conn.errorStream
            val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.e("StylistAI", "OpenRouter error ($responseCode): $errorText")
            return null
        }
    }

    private fun getToolsJson(): JSONArray {
        val properties = JSONObject().apply {
            put("brand", JSONObject().apply {
                put("type", "string")
                put("description", "Brand name, e.g. Nike, Puma, Adidas")
            })
            put("color", JSONObject().apply {
                put("type", "string")
                put("description", "Color of the shoe, e.g. black, white, red, blue")
            })
            put("occasion", JSONObject().apply {
                put("type", "string")
                put("description", "Occasion or usage, e.g. running, sports, casual, formal, party, wedding, gym")
            })
            put("shoeType", JSONObject().apply {
                put("type", "string")
                put("description", "Type of shoe, e.g. running, sneakers, casual, formal, loafers")
            })
            put("minPrice", JSONObject().apply {
                put("type", "number")
                put("description", "Minimum budget/price in rupees")
            })
            put("maxPrice", JSONObject().apply {
                put("type", "number")
                put("description", "Maximum budget/price in rupees")
            })
            put("size", JSONObject().apply {
                put("type", "string")
                put("description", "Shoe size, e.g. UK 6, UK 7, UK 8, UK 9, UK 10")
            })
        }

        val functionObj = JSONObject().apply {
            put("name", "search_products")
            put("description", "Search the shoe catalog. Use only what the customer asked for.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", properties)
            })
        }

        val toolObj = JSONObject().apply {
            put("type", "function")
            put("function", functionObj)
        }

        return JSONArray().apply { put(toolObj) }
    }

    private fun formatIndianRupees(amount: Double): String {
        val integerAmount = amount.toLong()
        val formatter = NumberFormat.getIntegerInstance(Locale("en", "IN"))
        return "₹" + formatter.format(integerAmount)
    }

    private fun cleanFilterArg(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        val ignoredWords = setOf("any", "all", "none", "no preference", "n/a", "null", "undefined", "no")
        if (ignoredWords.contains(lower)) return null
        return trimmed
    }

    private fun executeSearchProducts(argsJsonStr: String): Pair<String, List<ShoeProduct>> {
        Log.d("AI_SEARCH", "Arguments received from model: $argsJsonStr")

        val args = try {
            JSONObject(argsJsonStr)
        } catch (e: Exception) {
            JSONObject()
        }

        // Rule 1: Ignore any argument that is null, blank, or a word like "any", "all", "none", "no preference"
        val filterBrand = cleanFilterArg(args.optString("brand", null))
        val filterColor = cleanFilterArg(args.optString("color", null))
        val filterOccasion = cleanFilterArg(args.optString("occasion", null))
        val filterShoeType = cleanFilterArg(args.optString("shoeType", null))
        val filterSize = cleanFilterArg(args.optString("size", null))

        val filterMinPrice = if (args.has("minPrice") && !args.isNull("minPrice")) {
            val v = args.optDouble("minPrice")
            if (v > 0 && !v.isNaN()) v else null
        } else null

        val filterMaxPrice = if (args.has("maxPrice") && !args.isNull("maxPrice")) {
            val v = args.optDouble("maxPrice")
            if (v > 0 && !v.isNaN()) v else null
        } else null

        val snapshot = try {
            Tasks.await(FirebaseFirestore.getInstance().collection("products").limit(200).get())
        } catch (e: Exception) {
            Log.e("StylistAI", "Firestore query failed", e)
            Log.d("AI_SEARCH", "Loaded 0 products from Firestore (query failed)")
            Log.d("AI_SEARCH", "Matched 0 products")
            return Pair("[]", emptyList())
        }

        val allProducts = snapshot.documents.mapNotNull { doc ->
            val inStock = doc.getBoolean("inStock") ?: true
            if (!inStock) return@mapNotNull null

            val urls = (doc.get("imageUrls") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val primary = doc.getString("imageUrl") ?: ""
            val thumbnailUrl = urls.firstOrNull { !it.endsWith(".mp4") && !it.endsWith(".mov") && !it.endsWith(".webm") }
                ?: if (!primary.endsWith(".mp4") && !primary.endsWith(".mov") && !primary.endsWith(".webm")) primary else ""

            val name = doc.getString("name") ?: ""
            val price = doc.getString("price") ?: ""
            val priceNum = doc.getDouble("priceNum") ?: ProductFields.parsePrice(price)
            val discountPercent = (doc.getLong("discountPercent") ?: 0L).toInt()
            val finalPriceNum = doc.getDouble("finalPriceNum") ?: ProductFields.finalPrice(priceNum, discountPercent)
            val brand = doc.getString("brand") ?: ""
            val shoeType = doc.getString("shoeType") ?: ""
            val colors = (doc.get("colors") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val occasions = (doc.get("occasions") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val sizes = (doc.get("sizes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val description = doc.getString("description") ?: ""
            val specifications = doc.getString("specifications") ?: ""
            val productStory = doc.getString("productStory") ?: ""
            val features = doc.getString("features") ?: ""
            val details = doc.getString("details") ?: ""
            val avgRating = doc.getDouble("avgRating") ?: 0.0

            ShoeProduct(
                id = doc.id,
                name = name,
                price = price.ifEmpty { formatIndianRupees(finalPriceNum) },
                finalPriceNum = finalPriceNum,
                imageUrl = thumbnailUrl,
                imageUrls = urls.ifEmpty { if (thumbnailUrl.isNotEmpty()) listOf(thumbnailUrl) else emptyList() },
                description = description,
                specifications = specifications,
                productStory = productStory,
                features = features,
                details = details,
                brand = brand,
                shoeType = shoeType,
                discountPercent = discountPercent,
                inStock = inStock,
                colors = colors,
                occasions = occasions,
                sizes = sizes,
                avgRating = avgRating
            )
        }

        Log.d("AI_SEARCH", "Loaded ${allProducts.size} products from Firestore")

        // Rule 2 & 3: HARD filters (brand, maxPrice, minPrice, inStock) and shoeType
        val hardMatched = allProducts.filter { p ->
            // inStock must not be false
            if (!p.inStock) return@filter false

            // Brand: case-insensitive contains
            if (filterBrand != null) {
                if (p.brand.isBlank()) return@filter false
                val reqBrand = filterBrand.lowercase()
                val prodBrand = p.brand.lowercase()
                if (!prodBrand.contains(reqBrand) && !reqBrand.contains(prodBrand)) {
                    return@filter false
                }
            }

            // ShoeType: contains in either direction; if product has no shoeType, do not exclude it
            if (filterShoeType != null && p.shoeType.isNotBlank()) {
                val reqType = filterShoeType.lowercase()
                val prodType = p.shoeType.lowercase()
                if (!prodType.contains(reqType) && !reqType.contains(prodType)) {
                    return@filter false
                }
            }

            // Price range check
            if (filterMinPrice != null && p.finalPriceNum < filterMinPrice) {
                return@filter false
            }
            if (filterMaxPrice != null && p.finalPriceNum > filterMaxPrice) {
                return@filter false
            }

            true
        }

        // Rule 4: SOFT filters (color, occasion, size). Apply only to products that HAVE that field.
        val softMatched = hardMatched.filter { p ->
            // Color: apply only if product has colors; if not, keep it
            if (filterColor != null && p.colors.isNotEmpty()) {
                val reqColor = filterColor.lowercase()
                val match = p.colors.any { c ->
                    val cl = c.lowercase()
                    cl.contains(reqColor) || reqColor.contains(cl)
                }
                if (!match) return@filter false
            }

            // Occasion: apply only if product has occasions; if not, keep it
            if (filterOccasion != null && p.occasions.isNotEmpty()) {
                val reqOccasion = filterOccasion.lowercase()
                val match = p.occasions.any { o ->
                    val ol = o.lowercase()
                    ol.contains(reqOccasion) || reqOccasion.contains(ol)
                }
                if (!match) return@filter false
            }

            // Size: apply only if product has sizes; if not, keep it
            if (filterSize != null && p.sizes.isNotEmpty()) {
                val cleanTarget = filterSize.replace(" ", "").lowercase()
                val match = p.sizes.any { s ->
                    val cleanS = s.replace(" ", "").lowercase()
                    cleanS.contains(cleanTarget) || cleanTarget.contains(cleanS)
                }
                if (!match) return@filter false
            }

            true
        }

        // If applying soft filters leaves zero products, ignore soft filters
        val finalMatches = if (softMatched.isNotEmpty()) softMatched else hardMatched

        Log.d("AI_SEARCH", "Matched ${finalMatches.size} products")

        // Check if called with no filters
        val hasNoFilters = filterBrand == null && filterColor == null && filterOccasion == null &&
                filterShoeType == null && filterSize == null && filterMinPrice == null && filterMaxPrice == null

        // Rule 5: If result is still empty, return 3 cheapest in-stock products of requested shoeType (or any shoes)
        val isFallback: Boolean
        val resultProducts: List<ShoeProduct>

        if (finalMatches.isNotEmpty()) {
            isFallback = false
            resultProducts = if (hasNoFilters) {
                // When search_products is called with no filters, sort by avgRating (highest first, products without a rating last)
                finalMatches.sortedByDescending { it.avgRating }
            } else {
                finalMatches
            }
        } else {
            isFallback = true
            val candidatePool = if (filterShoeType != null) {
                val reqType = filterShoeType.lowercase()
                val ofType = allProducts.filter { p ->
                    p.inStock && (p.shoeType.isBlank() || p.shoeType.lowercase().contains(reqType) || reqType.contains(p.shoeType.lowercase()))
                }
                if (ofType.isNotEmpty()) ofType else allProducts.filter { it.inStock }
            } else {
                allProducts.filter { it.inStock }
            }
            resultProducts = candidatePool.sortedBy { it.finalPriceNum }.take(3)
        }

        // Return at most 5 products (or 3 fallback products) as JSON to the model
        val topList = resultProducts.take(if (isFallback) 3 else 5)
        val jsonArray = JSONArray()
        for (p in topList) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("brand", p.brand)
                put("shoeType", p.shoeType)
                put("priceText", formatIndianRupees(p.finalPriceNum))
                put("discountPercent", p.discountPercent)
                put("colors", JSONArray(p.colors))
                put("sizes", JSONArray(p.sizes))
            }
            jsonArray.put(obj)
        }

        val jsonResultString = if (isFallback) {
            JSONObject().apply {
                put("note", "no exact match, these are the closest")
                put("products", jsonArray)
            }.toString()
        } else {
            jsonArray.toString()
        }

        return Pair(jsonResultString, resultProducts)
    }

    // RecyclerView Adapter
    private class ChatAdapter(
        private val items: List<ChatMessage>,
        private val onProductClick: (ShoeProduct) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_USER = 1
            private const val TYPE_ASSISTANT = 2
        }

        override fun getItemViewType(position: Int): Int {
            return if (items[position].isUser) TYPE_USER else TYPE_ASSISTANT
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_USER) {
                val view = inflater.inflate(R.layout.item_chat_user, parent, false)
                UserViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_chat_assistant, parent, false)
                AssistantViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val message = items[position]
            if (holder is UserViewHolder) {
                holder.tvMessage.text = message.text
            } else if (holder is AssistantViewHolder) {
                holder.tvMessage.text = message.text

                if (message.products.isNotEmpty()) {
                    holder.rvProducts.visibility = View.VISIBLE
                    holder.rvProducts.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
                    holder.rvProducts.adapter = ProductCardAdapter(message.products, onProductClick)
                } else {
                    holder.rvProducts.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        }

        class AssistantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
            val rvProducts: RecyclerView = itemView.findViewById(R.id.rvProducts)
        }
    }

    // Adapter for Product Cards under Stylist Message
    private class ProductCardAdapter(
        private val products: List<ShoeProduct>,
        private val onProductClick: (ShoeProduct) -> Unit
    ) : RecyclerView.Adapter<ProductCardAdapter.CardViewHolder>() {

        class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivShoeThumb: ImageView = itemView.findViewById(R.id.ivShoeThumb)
            val tvShoeName: TextView = itemView.findViewById(R.id.tvShoeName)
            val tvShoePrice: TextView = itemView.findViewById(R.id.tvShoePrice)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stylist_product_card, parent, false)
            return CardViewHolder(view)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val product = products[position]
            holder.tvShoeName.text = product.name
            val formatter = NumberFormat.getIntegerInstance(Locale("en", "IN"))
            holder.tvShoePrice.text = "₹" + formatter.format(product.finalPriceNum.toLong())

            Glide.with(holder.itemView.context)
                .load(product.imageUrl)
                .placeholder(R.drawable.shoes211)
                .error(R.drawable.shoes211)
                .into(holder.ivShoeThumb)

            holder.itemView.setOnClickListener {
                onProductClick(product)
            }
        }

        override fun getItemCount(): Int = products.size
    }
}
