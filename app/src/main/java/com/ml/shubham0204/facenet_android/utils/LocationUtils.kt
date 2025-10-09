package com.ml.shubham0204.facenet_android.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val isFromGPS: Boolean
)

class LocationUtils(private val context: Context) {
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    /**
     * Verifica se as permissões de localização estão concedidas
     */
    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Obtém a localização atual do dispositivo
     * @param timeoutMs Timeout em milissegundos (padrão: 10000ms = 10s)
     * @return LocationResult com as coordenadas ou null se não conseguir obter
     */
    suspend fun getCurrentLocation(timeoutMs: Long = 10000): LocationResult? {
        if (!hasLocationPermissions()) {
            Log.w("LocationUtils", "❌ Permissões de localização não concedidas")
            return null
        }
        
        return try {
            Log.d("LocationUtils", "�� Iniciando busca de localização...")
            
            // Verificar se GPS está habilitado
            val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            Log.d("LocationUtils", "📡 GPS habilitado: $isGPSEnabled")
            Log.d("LocationUtils", "�� Network habilitado: $isNetworkEnabled")
            
            if (!isGPSEnabled && !isNetworkEnabled) {
                Log.w("LocationUtils", "❌ GPS e Network desabilitados")
                return null
            }
            
            // Tentar obter última localização conhecida primeiro
            val lastKnownLocation = getLastKnownLocation()
            if (lastKnownLocation != null) {
                val age = System.currentTimeMillis() - lastKnownLocation.time
                if (age < 300000) { // 5 minutos
                    Log.d("LocationUtils", "✅ Usando última localização conhecida (idade: ${age}ms)")
                    return LocationResult(
                        latitude = lastKnownLocation.latitude,
                        longitude = lastKnownLocation.longitude,
                        accuracy = lastKnownLocation.accuracy,
                        isFromGPS = lastKnownLocation.provider == LocationManager.GPS_PROVIDER
                    )
                }
            }
            
            // Solicitar nova localização
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        Log.d("LocationUtils", "✅ Nova localização obtida: ${location.latitude}, ${location.longitude}")
                        Log.d("LocationUtils", "   - Precisão: ${location.accuracy}m")
                        Log.d("LocationUtils", "   - Provedor: ${location.provider}")
                        
                        if (resumed.compareAndSet(false, true)) {
                            handler.removeCallbacksAndMessages(null)
                            locationManager.removeUpdates(this)
                            continuation.resume(
                                LocationResult(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracy = location.accuracy,
                                    isFromGPS = location.provider == LocationManager.GPS_PROVIDER
                                )
                            )
                        }
                    }
                    
                    override fun onProviderEnabled(provider: String) {
                        Log.d("LocationUtils", "📡 Provedor habilitado: $provider")
                    }
                    
                    override fun onProviderDisabled(provider: String) {
                        Log.w("LocationUtils", "�� Provedor desabilitado: $provider")
                    }
                }
                
                // Configurar timeout
                val timeoutRunnable = Runnable {
                    Log.w("LocationUtils", "⏰ Timeout na obtenção de localização")
                    if (resumed.compareAndSet(false, true)) {
                        locationManager.removeUpdates(locationListener)
                        continuation.resume(null)
                    }
                }
                handler.postDelayed(timeoutRunnable, timeoutMs)
                
                // Solicitar atualizações de localização
                try {
                    if (isGPSEnabled) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000L, // 1 segundo
                            1f, // 1 metro
                            locationListener
                        )
                        Log.d("LocationUtils", "📡 Solicitando atualizações do GPS")
                    }
                    
                    if (isNetworkEnabled) {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            1000L, // 1 segundo
                            1f, // 1 metro
                            locationListener
                        )
                        Log.d("LocationUtils", "📡 Solicitando atualizações da Network")
                    }
                } catch (e: SecurityException) {
                    Log.e("LocationUtils", "❌ Erro de segurança ao solicitar localização: ${e.message}")
                    handler.removeCallbacks(timeoutRunnable)
                    continuation.resume(null)
                }
                
                // Cleanup quando cancelado
                continuation.invokeOnCancellation {
                    Log.d("LocationUtils", "🔄 Cancelando busca de localização")
                    handler.removeCallbacks(timeoutRunnable)
                    locationManager.removeUpdates(locationListener)
                }
            }
            }
            
        } catch (e: Exception) {
            Log.e("LocationUtils", "❌ Erro ao obter localização: ${e.message}")
            null
        }
    }
    
    /**
     * Obtém a última localização conhecida
     */
    private fun getLastKnownLocation(): Location? {
        return try {
            val gpsLocation = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null
            
            val networkLocation = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null
            
            // Retornar a localização mais recente
            when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } catch (e: SecurityException) {
            Log.e("LocationUtils", "❌ Erro de segurança ao obter última localização: ${e.message}")
            null
        }
    }
}