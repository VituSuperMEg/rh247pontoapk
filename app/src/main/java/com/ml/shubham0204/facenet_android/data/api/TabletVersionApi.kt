package com.ml.shubham0204.facenet_android.data.api

import com.ml.shubham0204.facenet_android.data.model.TabletVersionData
import retrofit2.http.GET

interface TabletVersionApi {
    
    @GET("api/services/util/download-tablet-version")
    suspend fun checkTabletVersion(): TabletVersionData
} 