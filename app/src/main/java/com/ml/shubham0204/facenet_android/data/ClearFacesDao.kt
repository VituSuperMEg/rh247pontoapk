package com.ml.shubham0204.facenet_android.data

import com.ml.shubham0204.facenet_android.data.ObjectBoxStore
import io.objectbox.Box

class ClearFacesDao {
    
    private val personBox: Box<PersonRecord> = ObjectBoxStore.store.boxFor(PersonRecord::class.java)
    private val faceImageBox: Box<FaceImageRecord> = ObjectBoxStore.store.boxFor(FaceImageRecord::class.java)
    
    fun clearAllFaces() {
        try {
            // Limpar todas as faces
            personBox.removeAll()
            faceImageBox.removeAll()
        } catch (e: Exception) {
            throw e
        }
    }
    
    fun getFacesCount(): Int {
        return personBox.count().toInt()
    }
} 