package com.ml.shubham0204.facenet_android.utils

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object BitmapUtils {
    
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
            Log.d("BitmapUtils", "✅ Bitmap convertido para base64 (${base64.length} chars)")
            base64
        } catch (e: Exception) {
            Log.e("BitmapUtils", "❌ Erro ao converter bitmap para base64: ${e.message}")
            ""
        }
    }
    
    fun isValidBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0
    }
} 