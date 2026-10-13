package com.immensive.screencontroller.data

import com.immensive.screencontroller.protocol.Command
import com.immensive.screencontroller.protocol.CommandExecutionReport
import com.immensive.screencontroller.protocol.CommandResult
import com.immensive.screencontroller.protocol.ProtocolConstants
import com.immensive.screencontroller.protocol.ProtocolTcpClient

class MonitorCommandRepository(
    private val client: ProtocolTcpClient = ProtocolTcpClient(),
) {
    suspend fun sendCommand(
        host: String,
        port: Int,
        monitorId: UByte,
        command: Command,
        timeoutMs: Long = ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS,
    ): CommandResult = client.send(
        host = host,
        port = port,
        monitorId = monitorId,
        command = command,
        timeoutMs = timeoutMs,
    )

    suspend fun sendCommandWithReport(
        host: String,
        port: Int,
        monitorId: UByte,
        command: Command,
        timeoutMs: Long = ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS,
    ): CommandExecutionReport = client.sendWithReport(
        host = host,
        port = port,
        monitorId = monitorId,
        command = command,
        timeoutMs = timeoutMs,
    )
}
