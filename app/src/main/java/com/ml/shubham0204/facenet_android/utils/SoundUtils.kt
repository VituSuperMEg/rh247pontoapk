package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.IOException

/**
 * Utilitário para reprodução de sons no aplicativo
 */
object SoundUtils {
    
    private const val TAG = "SoundUtils"
    
    /**
     * Reproduz o som de beep quando um ponto é registrado
     * @param context Contexto da aplicação
     */
    fun playBeepSound(context: Context) {
        try {
            val mediaPlayer = MediaPlayer().apply {
                // Configurar atributos de áudio para melhor qualidade
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                
                // Configurar volume baseado no volume do sistema
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val volume = if (currentVolume > 0) currentVolume.toFloat() / maxVolume else 0.3f
                
                setVolume(volume, volume)
                
                // Configurar fonte do arquivo de som
                val assetFileDescriptor = context.assets.openFd("bep.wav")
                setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                
                // Configurar callbacks
                setOnPreparedListener { mp ->
                    Log.d(TAG, "🔊 Som preparado, iniciando reprodução...")
                    mp.start()
                }
                
                setOnCompletionListener { mp ->
                    Log.d(TAG, "✅ Som reproduzido com sucesso")
                    mp.release()
                }
                
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "❌ Erro ao reproduzir som: what=$what, extra=$extra")
                    mp.release()
                    true
                }
                
                // Preparar o MediaPlayer
                prepareAsync()
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Erro ao carregar arquivo de som: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro inesperado ao reproduzir som: ${e.message}")
        }
    }
    
    /**
     * Verifica se o arquivo de som existe nos assets
     * @param context Contexto da aplicação
     * @return true se o arquivo existe, false caso contrário
     */
    fun isSoundFileAvailable(context: Context): Boolean {
        return try {
            context.assets.open("bep.wav").use { 
                Log.d(TAG, "✅ Arquivo bep.wav encontrado nos assets")
                true 
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Arquivo bep.wav não encontrado nos assets: ${e.message}")
            false
        }
    }
}
