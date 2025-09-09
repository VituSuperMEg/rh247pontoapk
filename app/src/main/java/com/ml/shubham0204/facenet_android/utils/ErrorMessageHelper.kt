package com.ml.shubham0204.facenet_android.utils

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

object ErrorMessageHelper {
    
    /**
     * Converte mensagens de erro técnicas em mensagens amigáveis para o usuário
     */
    fun getFriendlyErrorMessage(exception: Throwable): String {
        return when (exception) {
            is UnknownHostException -> {
                when {
                    exception.message?.contains("api.rh247.com.br") == true -> {
                        "🌐 Não foi possível conectar ao servidor. Verifique sua conexão com a internet e tente novamente."
                    }
                    exception.message?.contains("No address associated with hostname") == true -> {
                        "🔍 Servidor não encontrado. Verifique se a URL do servidor está correta nas configurações."
                    }
                    else -> {
                        "🌐 Problema de conexão com o servidor. Verifique sua internet e tente novamente."
                    }
                }
            }
            is ConnectException -> {
                "🔌 Não foi possível conectar ao servidor. Verifique sua conexão com a internet."
            }
            is SocketTimeoutException -> {
                "⏰ A conexão com o servidor demorou muito para responder. Tente novamente em alguns instantes."
            }
            is UnknownServiceException -> {
                "🔒 Erro de segurança na conexão. Verifique as configurações de rede do dispositivo."
            }
            is IllegalArgumentException -> {
                when {
                    exception.message?.contains("URL") == true -> {
                        "🔗 URL inválida. Verifique as configurações do servidor."
                    }
                    else -> {
                        "⚠️ Dados inválidos fornecidos. Verifique as configurações."
                    }
                }
            }
            else -> {
                // Para outros tipos de erro, tentar extrair uma mensagem mais amigável
                val message = exception.message ?: "Erro desconhecido"
                when {
                    message.contains("timeout", ignoreCase = true) -> {
                        "⏰ Operação cancelada por tempo limite. Tente novamente."
                    }
                    message.contains("network", ignoreCase = true) -> {
                        "🌐 Problema de rede. Verifique sua conexão com a internet."
                    }
                    message.contains("server", ignoreCase = true) -> {
                        "🖥️ Problema no servidor. Tente novamente em alguns instantes."
                    }
                    message.contains("connection", ignoreCase = true) -> {
                        "🔌 Problema de conexão. Verifique sua internet."
                    }
                    message.contains("permission", ignoreCase = true) -> {
                        "🔐 Permissão negada. Verifique as configurações do aplicativo."
                    }
                    else -> {
                        "❌ Ocorreu um erro inesperado. Tente novamente ou entre em contato com o suporte."
                    }
                }
            }
        }
    }
    
    /**
     * Converte mensagens de erro de string em mensagens amigáveis
     */
    fun getFriendlyErrorMessage(errorMessage: String): String {
        return when {
            errorMessage.contains("Unable to resolve host", ignoreCase = true) -> {
                "🌐 Servidor não encontrado. Verifique sua conexão com a internet e a URL do servidor."
            }
            errorMessage.contains("No address associated with hostname", ignoreCase = true) -> {
                "🔍 Servidor não encontrado. Verifique se a URL do servidor está correta nas configurações."
            }
            errorMessage.contains("timeout", ignoreCase = true) -> {
                "⏰ A operação demorou muito para ser concluída. Tente novamente."
            }
            errorMessage.contains("connection", ignoreCase = true) -> {
                "🔌 Problema de conexão. Verifique sua internet e tente novamente."
            }
            errorMessage.contains("network", ignoreCase = true) -> {
                "🌐 Problema de rede. Verifique sua conexão com a internet."
            }
            errorMessage.contains("server", ignoreCase = true) -> {
                "🖥️ Problema no servidor. Tente novamente em alguns instantes."
            }
            errorMessage.contains("HTTP 404", ignoreCase = true) -> {
                "🔍 Serviço não encontrado no servidor. Verifique as configurações."
            }
            errorMessage.contains("HTTP 500", ignoreCase = true) -> {
                "🖥️ Erro interno do servidor. Tente novamente em alguns instantes."
            }
            errorMessage.contains("HTTP 401", ignoreCase = true) -> {
                "🔐 Acesso negado. Verifique suas credenciais de sincronização."
            }
            errorMessage.contains("HTTP 403", ignoreCase = true) -> {
                "🚫 Acesso proibido. Verifique suas permissões de sincronização."
            }
            else -> {
                "❌ Ocorreu um erro durante a operação. Tente novamente ou entre em contato com o suporte."
            }
        }
    }
    
    /**
     * Gera mensagem amigável para histórico de sincronização
     */
    fun getFriendlySyncMessage(originalMessage: String, isSuccess: Boolean): String {
        return if (isSuccess) {
            originalMessage // Manter mensagens de sucesso como estão
        } else {
            when {
                originalMessage.contains("Sincronização manual falhou", ignoreCase = true) -> {
                    "Sincronização manual não foi concluída: ${getFriendlyErrorMessage(originalMessage)}"
                }
                originalMessage.contains("Sincronização automática falhou", ignoreCase = true) -> {
                    "Sincronização automática não foi concluída: ${getFriendlyErrorMessage(originalMessage)}"
                }
                originalMessage.contains("Erro na sincronização", ignoreCase = true) -> {
                    "Problema durante a sincronização: ${getFriendlyErrorMessage(originalMessage)}"
                }
                else -> {
                    getFriendlyErrorMessage(originalMessage)
                }
            }
        }
    }
}
