package com.ml.shubham0204.facenet_android.presentation.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.facenet_android.data.config.AppPreferences
import com.ml.shubham0204.facenet_android.data.model.EntidadeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinViewModel
class HomeViewModel : ViewModel(), KoinComponent {
    
    private val appPreferences: AppPreferences by inject()
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        carregarInformacoesEntidade()
    }
    
    private fun carregarInformacoesEntidade() {
        viewModelScope.launch {
            val entidadeInfo = appPreferences.entidadeInfo
            _uiState.update { 
                it.copy(entidadeInfo = entidadeInfo)
            }
        }
    }
    
    fun atualizarInformacoesEntidade() {
        carregarInformacoesEntidade()
    }
}

data class HomeUiState(
    val entidadeInfo: EntidadeInfo? = null
)
