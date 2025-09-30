package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object DeviceMacUtils {
    
    private const val TAG = "DeviceMacUtils"
    
    /**
     * Obtém o MAC do dispositivo e retorna criptografado em SHA-256
     * @param context Contexto da aplicação
     * @return MAC do dispositivo criptografado em SHA-256 ou null se não conseguir obter
     */
    fun getMacDispositivoCriptografado(context: Context): String? {
        return try {
            val macAddress = obterMacDispositivo(context)
            if (macAddress != null) {
                val macCriptografado = criptografarSha256(macAddress)
                Log.d(TAG, "✅ MAC do dispositivo obtido e criptografado: ${macCriptografado.take(8)}...")
                macCriptografado
            } else {
                Log.w(TAG, "⚠️ Não foi possível obter MAC do dispositivo")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao obter MAC criptografado: ${e.message}")
            null
        }
    }
    
    /**
     * Obtém o MAC do dispositivo usando diferentes métodos dependendo da versão do Android
     */
    private fun obterMacDispositivo(context: Context): String? {
        return try {
            when {
                // Android 6.0+ (API 23+): Usar Android ID como fallback
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    val androidId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                    if (androidId != null && androidId != "9774d56d682e549c") {
                        Log.d(TAG, "📱 Usando Android ID como identificador único")
                        "ANDROID_ID_$androidId"
                    } else {
                        // Fallback para MAC do WiFi se disponível
                        obterMacWifi(context) ?: gerarIdentificadorUnico()
                    }
                }
                // Android < 6.0: Tentar obter MAC do WiFi
                else -> {
                    obterMacWifi(context) ?: gerarIdentificadorUnico()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao obter MAC do dispositivo: ${e.message}")
            gerarIdentificadorUnico()
        }
    }
    
    /**
     * Tenta obter o MAC do WiFi (pode não funcionar em versões mais recentes)
     */
    private fun obterMacWifi(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val macAddress = wifiInfo.macAddress
            
            if (macAddress != null && macAddress != "02:00:00:00:00:00") {
                Log.d(TAG, "📶 MAC do WiFi obtido: ${macAddress.take(8)}...")
                macAddress
            } else {
                Log.w(TAG, "⚠️ MAC do WiFi não disponível ou inválido")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao obter MAC do WiFi: ${e.message}")
            null
        }
    }
    
    /**
     * Gera um identificador único baseado em características do dispositivo
     */
    private fun gerarIdentificadorUnico(): String {
        val deviceInfo = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.SERIAL}_${Build.FINGERPRINT}"
        Log.d(TAG, "🔧 Gerando identificador único baseado em características do dispositivo")
        return "DEVICE_$deviceInfo"
    }
    
    /**
     * Criptografa uma string usando SHA-256
     */
    private fun criptografarSha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray())
            
            // Converter bytes para hexadecimal
            val hexString = StringBuilder()
            for (byte in hash) {
                val hex = Integer.toHexString(0xff and byte.toInt())
                if (hex.length == 1) {
                    hexString.append('0')
                }
                hexString.append(hex)
            }
            
            hexString.toString().uppercase()
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "❌ Algoritmo SHA-256 não disponível: ${e.message}")
            // Fallback: usar hash simples
            input.hashCode().toString()
        }
    }
    
    /**
     * Valida se um MAC criptografado tem o formato correto (64 caracteres hexadecimais)
     */
    fun validarMacCriptografado(macCriptografado: String): Boolean {
        return macCriptografado.length == 64 && macCriptografado.matches(Regex("[0-9A-F]+"))
    }
    
    /**
     * Obtém informações do dispositivo para debug
     */
    fun getDeviceInfo(context: Context): Map<String, String> {
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT.toString(),
            "serial" to Build.SERIAL,
            "android_id" to (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "null"),
            "mac_criptografado" to (getMacDispositivoCriptografado(context) ?: "null")
        )
    }
}
