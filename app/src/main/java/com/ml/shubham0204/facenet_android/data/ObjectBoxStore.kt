package com.ml.shubham0204.facenet_android.data

import android.content.Context
import android.util.Log
import io.objectbox.BoxStore
import java.io.File

object ObjectBoxStore {
    private const val TAG = "ObjectBoxStore"
    
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        try {
            cleanupObjectBoxDirectory(context)
            
            store = MyObjectBox.builder()
                .androidContext(context)
                .build()
                
            Log.d(TAG, "ObjectBox initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectBox", e)
            throw e
        }
    }
    
    private fun cleanupObjectBoxDirectory(context: Context) {
        val objectBoxDir = File(context.filesDir, "objectbox")
        val nestedObjectBoxDir = File(objectBoxDir, "objectbox")
        
        Log.d(TAG, "Cleaning up ObjectBox directory structure")
        Log.d(TAG, "Main ObjectBox dir: ${objectBoxDir.absolutePath}")
        Log.d(TAG, "Nested ObjectBox dir: ${nestedObjectBoxDir.absolutePath}")
        
        // Check and clean up nested objectbox directory
        if (nestedObjectBoxDir.exists() && nestedObjectBoxDir.isFile) {
            Log.w(TAG, "Nested ObjectBox path exists as file, deleting it: ${nestedObjectBoxDir.absolutePath}")
            nestedObjectBoxDir.delete()
        }
        
        // Check and clean up main objectbox directory
        if (objectBoxDir.exists() && objectBoxDir.isFile) {
            Log.w(TAG, "Main ObjectBox path exists as file, deleting it: ${objectBoxDir.absolutePath}")
            objectBoxDir.delete()
        }
        
        // If the entire objectbox directory structure is corrupted, remove it completely
        if (objectBoxDir.exists() && objectBoxDir.isDirectory) {
            val hasCorruptedFiles = objectBoxDir.listFiles()?.any { file ->
                file.name == "objectbox" && file.isFile
            } ?: false
            
            if (hasCorruptedFiles) {
                Log.w(TAG, "Found corrupted ObjectBox structure, removing entire directory")
                objectBoxDir.deleteRecursively()
            }
        }
        
        // Ensure the directory structure exists
        if (!objectBoxDir.exists()) {
            objectBoxDir.mkdirs()
            Log.d(TAG, "Created ObjectBox directory: ${objectBoxDir.absolutePath}")
        }
    }
}
