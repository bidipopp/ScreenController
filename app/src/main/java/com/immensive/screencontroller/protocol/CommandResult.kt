package com.immensive.screencontroller.protocol

/**
 * Résultat de l'envoi d'une commande via TCP.
 *
 * Source note:
 * - section "Communication Procedure" (ACK/NACK/NAV)
 * - section "Communication Control" (Status = 0x00, 0x03, 0x04)
 */
sealed interface CommandResult {
    data object Ack : CommandResult
    data object Nack : CommandResult
    data object Nav : CommandResult
    data object Timeout : CommandResult
    data class ConnectionRefused(val cause: Throwable? = null) : CommandResult
    data class UnknownHost(val cause: Throwable? = null) : CommandResult
    data class ConnectionClosed(val cause: Throwable? = null) : CommandResult
    data class Error(val message: String, val cause: Throwable? = null) : CommandResult
}
