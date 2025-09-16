package com.ml.shubham0204.facenet_android.utils

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Environment
import android.os.StatFs
import java.io.File

object USBUtils {
    
    /**
     * Verifica se há pen drives USB conectados
     */
    fun hasUSBStorageConnected(context: Context): Boolean {
        return getUSBStoragePaths(context).isNotEmpty()
    }
    
    /**
     * Obtém os caminhos dos pen drives USB conectados
     */
    fun getUSBStoragePaths(context: Context): List<String> {
        val usbPaths = mutableListOf<String>()
        
        try {
            // Verificar pontos de montagem comuns para USB
            val mountPoints = listOf(
                "/mnt/usb",
                "/mnt/usbdisk",
                "/mnt/usb_storage",
                "/mnt/usbdisk1",
                "/mnt/usbdisk2",
                "/mnt/usbdisk3",
                "/mnt/usbdisk4",
                "/storage/usbdisk",
                "/storage/usbdisk1",
                "/storage/usbdisk2",
                "/storage/usbdisk3",
                "/storage/usbdisk4",
                "/storage/usbotg",
                "/storage/usbotg1",
                "/storage/usbotg2"
            )
            
            for (path in mountPoints) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory && dir.canRead() && dir.canWrite()) {
                    // Verificar se é realmente um dispositivo USB
                    if (isUSBDevice(path)) {
                        usbPaths.add(path)
                    }
                }
            }
            
            // Verificar também em /storage/ para dispositivos USB
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.startsWith("usb") || 
                        file.name.startsWith("usbotg") ||
                        file.name.contains("usb")) {
                        if (file.canRead() && file.canWrite() && isUSBDevice(file.absolutePath)) {
                            usbPaths.add(file.absolutePath)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return usbPaths
    }
    
    /**
     * Verifica se um caminho é realmente um dispositivo USB
     */
    private fun isUSBDevice(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists() || !file.isDirectory) return false
            
            // Verificar se tem espaço disponível (dispositivos USB geralmente têm espaço)
            val stat = StatFs(path)
            val availableBytes = stat.availableBytes
            val totalBytes = stat.totalBytes
            
            // Verificar se é um dispositivo com tamanho razoável (mais que 1MB)
            totalBytes > 1024 * 1024 && availableBytes > 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Obtém informações sobre o pen drive USB
     */
    fun getUSBStorageInfo(context: Context): USBStorageInfo? {
        val usbPaths = getUSBStoragePaths(context)
        if (usbPaths.isEmpty()) return null
        
        val path = usbPaths.first()
        return try {
            val stat = StatFs(path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            
            USBStorageInfo(
                path = path,
                totalSpace = totalBytes,
                availableSpace = availableBytes,
                usedSpace = usedBytes,
                isWritable = File(path).canWrite()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Cria um arquivo de backup no pen drive USB
     */
    fun createBackupOnUSB(context: Context, backupData: String, fileName: String): Result<String> {
        return try {
            val usbInfo = getUSBStorageInfo(context)
            if (usbInfo == null) {
                return Result.failure(Exception("Nenhum pen drive USB encontrado"))
            }
            
            if (!usbInfo.isWritable) {
                return Result.failure(Exception("Pen drive USB não tem permissão de escrita"))
            }
            
            val backupFile = File(usbInfo.path, fileName)
            backupFile.writeText(backupData)
            
            Result.success(backupFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Lê um arquivo de backup do pen drive USB
     */
    fun readBackupFromUSB(context: Context, fileName: String): Result<String> {
        return try {
            val usbInfo = getUSBStorageInfo(context)
            if (usbInfo == null) {
                return Result.failure(Exception("Nenhum pen drive USB encontrado"))
            }
            
            val backupFile = File(usbInfo.path, fileName)
            if (!backupFile.exists()) {
                return Result.failure(Exception("Arquivo de backup não encontrado no pen drive"))
            }
            
            val content = backupFile.readText()
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class USBStorageInfo(
    val path: String,
    val totalSpace: Long,
    val availableSpace: Long,
    val usedSpace: Long,
    val isWritable: Boolean
) {
    fun getTotalSpaceFormatted(): String {
        return formatBytes(totalSpace)
    }
    
    fun getAvailableSpaceFormatted(): String {
        return formatBytes(availableSpace)
    }
    
    fun getUsedSpaceFormatted(): String {
        return formatBytes(usedSpace)
    }
    
    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }
}
