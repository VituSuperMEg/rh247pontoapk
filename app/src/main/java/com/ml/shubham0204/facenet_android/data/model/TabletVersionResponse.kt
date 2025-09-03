package com.ml.shubham0204.facenet_android.data.model

import com.google.gson.annotations.SerializedName

// A API retorna os dados diretamente, sem wrapper
data class TabletVersionData(
    @SerializedName("version")
    val version: String,
    
    @SerializedName("filename")
    val filename: String,
    
    @SerializedName("file_size")
    val fileSize: Long,
    
    @SerializedName("file_size_formatted")
    val fileSizeFormatted: String,
    
    @SerializedName("last_modified")
    val lastModified: String,
    
    @SerializedName("download_url")
    val downloadUrl: String,
    
    @SerializedName("available")
    val available: Boolean
) 