package com.immensive.screencontroller.protocol

/**
 * Étapes instrumentées du cycle d'envoi TCP.
 */
enum class CommandExecutionStep {
    CONNECT,
    WRITE,
    READ,
    PARSE,
}

/**
 * Statut d'une étape d'exécution.
 */
enum class CommandExecutionStepStatus {
    OK,
    FAIL,
}

/**
 * Détail d'une étape instrumentée.
 */
data class CommandExecutionStepDetail(
    val step: CommandExecutionStep,
    val status: CommandExecutionStepStatus,
    val message: String,
    val durationMs: Long,
)

/**
 * Rapport d'exécution complet pour un envoi de commande.
 */
data class CommandExecutionReport(
    val commandResult: CommandResult,
    val timeoutMs: Long,
    val totalDurationMs: Long,
    val txHex: String,
    val rxHex: String?,
    val steps: List<CommandExecutionStepDetail>,
)

