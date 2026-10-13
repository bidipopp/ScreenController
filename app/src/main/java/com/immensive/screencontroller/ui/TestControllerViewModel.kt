package com.immensive.screencontroller.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immensive.screencontroller.data.MonitorCommandRepository
import com.immensive.screencontroller.protocol.ColorTemperatureCommand
import com.immensive.screencontroller.protocol.ColorTemperatureValue
import com.immensive.screencontroller.protocol.Command
import com.immensive.screencontroller.protocol.CommandExecutionReport
import com.immensive.screencontroller.protocol.CommandExecutionStep
import com.immensive.screencontroller.protocol.CommandResult
import com.immensive.screencontroller.protocol.IrRemoteLockMode
import com.immensive.screencontroller.protocol.PowerCommand
import com.immensive.screencontroller.protocol.PowerState
import com.immensive.screencontroller.protocol.ProtocolConstants
import com.immensive.screencontroller.protocol.UserInputControlCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TestControllerViewModel(
    private val repository: MonitorCommandRepository = MonitorCommandRepository(),
) : ViewModel() {
    private companion object {
        const val TIMEOUT_PREFIX = "timeout:"
        const val MAX_TIMEOUT_MS: Long = Int.MAX_VALUE.toLong()
    }

    var uiState by mutableStateOf(TestControllerUiState())
        private set

    private var currentJob: Job? = null

    fun updateHost(value: String) {
        uiState = uiState.copy(host = value, inputError = null)
    }

    fun updatePort(value: String) {
        // Bridge UI: TestControllerScreen expose déjà onPortChanged sans callback dédié timeout.
        // Le champ timeout préfixe la valeur avec "timeout:" pour éviter un refactor hors périmètre.
        if (value.startsWith(TIMEOUT_PREFIX)) {
            uiState = uiState.copy(
                timeoutMs = value.removePrefix(TIMEOUT_PREFIX),
                inputError = null,
            )
            return
        }
        uiState = uiState.copy(port = value, inputError = null)
    }

    fun updateMonitorId(value: String) {
        uiState = uiState.copy(monitorId = value, inputError = null)
    }

    fun cancelCurrent() {
        currentJob?.cancel()
        currentJob = null
        uiState = uiState.copy(
            isSending = false,
            lastResult = "Action annulée",
            diagnostic = "Exécution annulée avant la fin.",
        )
    }

    fun send(action: TestAction) {
        val host = uiState.host.trim()
        val port = uiState.port.toIntOrNull()
        val monitorId = parseMonitorId(uiState.monitorId)
        val timeoutMs = uiState.timeoutMs.trim().toLongOrNull()

        when {
            host.isEmpty() -> {
                uiState = uiState.withInputError("IP/host requis")
                return
            }

            port == null || port !in 1..65535 -> {
                uiState = uiState.withInputError("Port invalide (1..65535)")
                return
            }

            monitorId == null -> {
                uiState = uiState.withInputError("Monitor ID invalide (0..255)")
                return
            }

            timeoutMs == null || timeoutMs !in 1L..MAX_TIMEOUT_MS -> {
                uiState = uiState.withInputError("Timeout invalide (1..$MAX_TIMEOUT_MS ms)")
                return
            }
        }

        currentJob?.cancel()
        uiState = uiState.copy(
            isSending = true,
            inputError = null,
            lastAction = action.label,
            lastResult = "Envoi en cours…",
            diagnostic = "Collecte du diagnostic en cours…",
        )

        currentJob = viewModelScope.launch {
            val report = repository.sendCommandWithReport(
                host = host,
                port = port,
                monitorId = monitorId,
                command = action.command,
                timeoutMs = timeoutMs,
            )
            uiState = uiState.copy(
                isSending = false,
                lastResult = report.commandResult.toReadableText(timeoutMs = timeoutMs),
                diagnostic = report.toDiagnosticText(),
            )
        }
    }

    private fun parseMonitorId(raw: String): UByte? {
        val input = raw.trim()
        if (input.isEmpty()) return ProtocolConstants.DEFAULT_MONITOR_ID

        val parsed = if (input.startsWith("0x", ignoreCase = true)) {
            input.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
        } else {
            input.toIntOrNull()
        }
        return parsed?.takeIf { it in 0..255 }?.toUByte()
    }
}

data class TestControllerUiState(
    val host: String = "",
    val port: String = ProtocolConstants.LAN_TCP_PORT.toString(),
    val monitorId: String = ProtocolConstants.DEFAULT_MONITOR_ID.toString(),
    val timeoutMs: String = ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS.toString(),
    val isSending: Boolean = false,
    val lastAction: String? = null,
    val lastResult: String = "Aucune action envoyée",
    val diagnostic: String = "Aucun diagnostic disponible.",
    val inputError: String? = null,
)

private fun TestControllerUiState.withInputError(message: String): TestControllerUiState = copy(
    inputError = message,
    diagnostic = "Entrée invalide: $message",
)

enum class TestAction(val label: String, val command: Command) {
    POWER_ON("Power ON", PowerCommand.Set(PowerState.ON)),
    POWER_OFF("Power OFF", PowerCommand.Set(PowerState.OFF)),
    REMOTE_LOCK("Remote Lock", UserInputControlCommand.Set(IrRemoteLockMode.LOCK_ALL)),
    REMOTE_UNLOCK("Remote Unlock", UserInputControlCommand.Set(IrRemoteLockMode.UNLOCK_ALL)),
    COLOR_6500("Color 6500K", ColorTemperatureCommand.Set(ColorTemperatureValue.K6500)),
    COLOR_7500("Color 7500K", ColorTemperatureCommand.Set(ColorTemperatureValue.K7500)),
    COLOR_9300("Color 9300K", ColorTemperatureCommand.Set(ColorTemperatureValue.K9300)),
}

private fun CommandResult.toReadableText(timeoutMs: Long): String = when (this) {
    CommandResult.Ack -> "ACK (succès)"
    CommandResult.Nack -> "NACK (commande refusée)"
    CommandResult.Nav -> "NAV (valeur non disponible)"
    CommandResult.Timeout -> "Timeout après ${timeoutMs} ms (connexion ou réponse)"
    is CommandResult.ConnectionRefused -> "Connexion refusée (écran injoignable sur le port TCP)"
    is CommandResult.UnknownHost -> "Hôte introuvable (adresse IP/nom invalide)"
    is CommandResult.ConnectionClosed -> "Connexion fermée (socket coupée pendant l'échange)"
    is CommandResult.Error -> "Erreur: $message"
}

private fun CommandExecutionReport.toDiagnosticText(): String = buildString {
    appendLine("Timeout configuré: ${timeoutMs} ms")
    appendLine("Durée totale: ${totalDurationMs} ms")
    appendLine("TX: $txHex")
    appendLine("RX: ${rxHex ?: "n/a"}")
    appendLine("Étapes:")
    if (steps.isEmpty()) {
        append("- (aucune étape)")
    } else {
        steps.forEach { step ->
            append("- ")
            append(step.step.toReadableLabel())
            append(" | ")
            append(step.status.name)
            append(" | ")
            append(step.durationMs)
            append(" ms")
            append(" | ")
            append(step.message)
            appendLine()
        }
    }
}

private fun CommandExecutionStep.toReadableLabel(): String = when (this) {
    CommandExecutionStep.CONNECT -> "CONNECT"
    CommandExecutionStep.WRITE -> "WRITE"
    CommandExecutionStep.READ -> "READ"
    CommandExecutionStep.PARSE -> "PARSE"
}
