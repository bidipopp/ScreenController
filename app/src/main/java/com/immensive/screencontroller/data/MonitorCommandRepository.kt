package com.immensive.screencontroller.data

import com.immensive.screencontroller.protocol.Command
import com.immensive.screencontroller.protocol.CommandResult
import com.immensive.screencontroller.protocol.ProtocolTcpClient

class MonitorCommandRepository(
    private val client: ProtocolTcpClient = ProtocolTcpClient(),
) {
    suspend fun sendCommand(
        host: String,
        port: Int,
        monitorId: UByte,
        command: Command,
    ): CommandResult = client.send(
        host = host,
        port = port,
        monitorId = monitorId,
        command = command,
    )
}
