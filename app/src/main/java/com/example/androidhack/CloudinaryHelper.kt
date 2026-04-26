package com.example.androidhack

/**
 * Extension function to apply Cloudinary image optimization parameters (f_auto, q_auto)
 * and optional width constraint for better performance and reduced bandwidth usage.
 */
fun String?.optimizeCloudinaryUrl(width: Int? = null): String? {
    if (this == null) return null
    if (!this.contains("res.cloudinary.com")) return this
    
    // Only apply if not already optimized in the URL
    if (this.contains("/upload/") && !this.contains("f_auto") && !this.contains("q_auto")) {
        val transformations = mutableListOf("f_auto", "q_auto")
        if (width != null) {
            transformations.add("w_$width")
        }
        val transformationsStr = transformations.joinToString(",")
        return this.replaceFirst("/upload/", "/upload/$transformationsStr/")
    }
    return this
}
