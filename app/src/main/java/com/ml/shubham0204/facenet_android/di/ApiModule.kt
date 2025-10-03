package com.ml.shubham0204.facenet_android.di

import com.ml.shubham0204.facenet_android.data.api.TabletVersionApi
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.data.config.ServerConfig
import com.ml.shubham0204.facenet_android.data.repository.TabletUpdateRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val apiModule = module {
    
    single {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .connectTimeout(ServerConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ServerConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ServerConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    single {
        Retrofit.Builder()
            .baseUrl(ServerConfig.BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    single {
        get<Retrofit>().create(TabletVersionApi::class.java)
    }
    
    single {
        TabletUpdateRepository(
            api = get(),
            context = get()
        )
    }
    
    single {
        AppPreferences(get())
    }
} 