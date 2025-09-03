package com.ml.shubham0204.facenet_android.data.config

object ServerConfig {
    // URL base do servidor - deve ser configurável
    const val BASE_URL = "https://api.rh247.com.br/"
    
    // Endpoints da API
    const val CHECK_VERSION_ENDPOINT = "api/services/util/download-tablet-version"
    const val DOWNLOAD_ENDPOINT = "230440023/services/util/download-apk"
    
    // Timeout das requisições
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
} 