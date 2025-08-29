package com.ml.shubham0204.facenet_android.di

import com.ml.shubham0204.facenet_android.data.FuncionariosDao
import com.ml.shubham0204.facenet_android.data.PontosGenericosDao
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.ml.shubham0204.facenet_android")
class AppModule {
    
    @Single
    fun providePontosGenericosDao(): PontosGenericosDao {
        return PontosGenericosDao()
    }
    
    @Single
    fun provideFuncionariosDao(): FuncionariosDao {
        return FuncionariosDao()
    }
}
