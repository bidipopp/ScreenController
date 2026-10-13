package com.immensive.screencontroller.protocol

import java.io.Closeable
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Client TCP asynchrone minimal pour la couche protocole.
 *
 * Source note:
 * - "For LAN control, the port number is 5000"
 * - "Communication Procedure" (réponse attendue ACK/NACK/NAV ou report Get).
 */
class ProtocolTcpClient(
    private val connectionFactory: TcpConnectionFactory = JvmSocketConnectionFactory(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private companion object {
        const val MAX_TIMEOUT_MS: Long = Int.MAX_VALUE.toLong()
    }

    suspend fun send(
        host: String,
        command: Command,
        monitorId: UByte = ProtocolConstants.DEFAULT_MONITOR_ID,
        port: Int = ProtocolConstants.LAN_TCP_PORT,
        timeoutMs: Long = ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS,
    ): CommandResult = sendWithReport(
        host = host,
        command = command,
        monitorId = monitorId,
        port = port,
        timeoutMs = timeoutMs,
    ).commandResult

    suspend fun sendWithReport(
        host: String,
        command: Command,
        monitorId: UByte = ProtocolConstants.DEFAULT_MONITOR_ID,
        port: Int = ProtocolConstants.LAN_TCP_PORT,
        timeoutMs: Long = ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS,
    ): CommandExecutionReport = withContext(ioDispatcher) {
        val totalStartNs = System.nanoTime()
        val txFrame = command.toFrame(monitorId)
        val stepDetails = mutableListOf<CommandExecutionStepDetail>()
        var rxBytes: ByteArray? = null

        fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000

        fun recordStep(
            step: CommandExecutionStep,
            status: CommandExecutionStepStatus,
            message: String,
            startNs: Long,
        ) {
            stepDetails += CommandExecutionStepDetail(
                step = step,
                status = status,
                message = message,
                durationMs = elapsedMs(startNs),
            )
        }

        fun timeoutMessage(step: CommandExecutionStep): String = when (step) {
            CommandExecutionStep.CONNECT -> "Timeout de connexion"
            CommandExecutionStep.READ -> "Timeout d'attente de réponse"
            CommandExecutionStep.WRITE -> "Timeout d'écriture"
            CommandExecutionStep.PARSE -> "Timeout de parsing"
        }

        fun makeReport(result: CommandResult): CommandExecutionReport = CommandExecutionReport(
            commandResult = result,
            timeoutMs = timeoutMs,
            totalDurationMs = elapsedMs(totalStartNs),
            txHex = txFrame.toHexString(),
            rxHex = rxBytes?.takeIf { it.isNotEmpty() }?.toHexString(),
            steps = stepDetails.toList(),
        )

        if (timeoutMs !in 1L..MAX_TIMEOUT_MS) {
            val message = "Timeout invalide: $timeoutMs ms (attendu: 1..$MAX_TIMEOUT_MS)"
            return@withContext makeReport(
                CommandResult.Error(message, IllegalArgumentException(message))
            )
        }

        var currentStep: CommandExecutionStep? = null

        try {
            withTimeout(timeoutMs) {
                currentStep = CommandExecutionStep.CONNECT
                val connectStartNs = System.nanoTime()
                val connection = try {
                    val createdConnection = connectionFactory.connect(host, port, timeoutMs.toInt())
                    recordStep(
                        step = CommandExecutionStep.CONNECT,
                        status = CommandExecutionStepStatus.OK,
                        message = "Connexion TCP établie",
                        startNs = connectStartNs,
                    )
                    createdConnection
                } catch (t: Throwable) {
                    val message = if (t is SocketTimeoutException) {
                        timeoutMessage(CommandExecutionStep.CONNECT)
                    } else {
                        t.message ?: "Échec de connexion"
                    }
                    recordStep(
                        step = CommandExecutionStep.CONNECT,
                        status = CommandExecutionStepStatus.FAIL,
                        message = message,
                        startNs = connectStartNs,
                    )
                    throw t
                }

                connection.use {
                    currentStep = CommandExecutionStep.WRITE
                    val writeStartNs = System.nanoTime()
                    try {
                        connection.write(txFrame)
                        recordStep(
                            step = CommandExecutionStep.WRITE,
                            status = CommandExecutionStepStatus.OK,
                            message = "Trame envoyée (${txFrame.size} octets)",
                            startNs = writeStartNs,
                        )
                    } catch (t: Throwable) {
                        val message = if (t is SocketTimeoutException) {
                            timeoutMessage(CommandExecutionStep.WRITE)
                        } else {
                            t.message ?: "Échec d'écriture TCP"
                        }
                        recordStep(
                            step = CommandExecutionStep.WRITE,
                            status = CommandExecutionStepStatus.FAIL,
                            message = message,
                            startNs = writeStartNs,
                        )
                        throw t
                    }

                    currentStep = CommandExecutionStep.READ
                    val readStartNs = System.nanoTime()
                    val response = try {
                        connection.read()
                    } catch (t: Throwable) {
                        val message = if (t is SocketTimeoutException) {
                            timeoutMessage(CommandExecutionStep.READ)
                        } else {
                            t.message ?: "Échec de lecture TCP"
                        }
                        recordStep(
                            step = CommandExecutionStep.READ,
                            status = CommandExecutionStepStatus.FAIL,
                            message = message,
                            startNs = readStartNs,
                        )
                        throw t
                    }
                    rxBytes = response
                    if (response.isEmpty()) {
                        recordStep(
                            step = CommandExecutionStep.READ,
                            status = CommandExecutionStepStatus.FAIL,
                            message = "Connexion fermée par le pair (0 octet lu)",
                            startNs = readStartNs,
                        )
                        return@withTimeout makeReport(CommandResult.ConnectionClosed())
                    }
                    recordStep(
                        step = CommandExecutionStep.READ,
                        status = CommandExecutionStepStatus.OK,
                        message = "Réponse reçue (${response.size} octets)",
                        startNs = readStartNs,
                    )

                    currentStep = CommandExecutionStep.PARSE
                    val parseStartNs = System.nanoTime()
                    val parsed = ResponseParser.parse(response)
                    val parseStatus = if (parsed is CommandResult.Error) {
                        CommandExecutionStepStatus.FAIL
                    } else {
                        CommandExecutionStepStatus.OK
                    }
                    val parseMessage = when (parsed) {
                        CommandResult.Ack -> "Réponse ACK"
                        CommandResult.Nack -> "Réponse NACK"
                        CommandResult.Nav -> "Réponse NAV"
                        is CommandResult.Error -> parsed.message
                        else -> "Réponse analysée"
                    }
                    recordStep(
                        step = CommandExecutionStep.PARSE,
                        status = parseStatus,
                        message = parseMessage,
                        startNs = parseStartNs,
                    )

                    makeReport(parsed)
                }
            }
        } catch (_: TimeoutCancellationException) {
            if (currentStep != null && stepDetails.lastOrNull()?.step != currentStep) {
                val fallbackStartNs = System.nanoTime()
                recordStep(
                    step = currentStep!!,
                    status = CommandExecutionStepStatus.FAIL,
                    message = timeoutMessage(currentStep!!),
                    startNs = fallbackStartNs,
                )
            }
            makeReport(CommandResult.Timeout)
        } catch (_: SocketTimeoutException) {
            if (currentStep != null && stepDetails.lastOrNull()?.step != currentStep) {
                val fallbackStartNs = System.nanoTime()
                recordStep(
                    step = currentStep!!,
                    status = CommandExecutionStepStatus.FAIL,
                    message = timeoutMessage(currentStep!!),
                    startNs = fallbackStartNs,
                )
            }
            makeReport(CommandResult.Timeout)
        } catch (e: ConnectException) {
            makeReport(CommandResult.ConnectionRefused(e))
        } catch (e: UnknownHostException) {
            makeReport(CommandResult.UnknownHost(e))
        } catch (e: SocketException) {
            makeReport(CommandResult.ConnectionClosed(e))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            makeReport(CommandResult.Error("Erreur TCP", t))
        }
    }
}

interface TcpConnectionFactory {
    suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection
}

interface TcpConnection : Closeable {
    suspend fun write(data: ByteArray)
    suspend fun read(maxBytes: Int = 1024): ByteArray
}

class JvmSocketConnectionFactory : TcpConnectionFactory {
    override suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            SocketTcpConnection(socket)
        }
}

private class SocketTcpConnection(private val socket: Socket) : TcpConnection {
    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        socket.getOutputStream().write(data)
        socket.getOutputStream().flush()
    }

    override suspend fun read(maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        val buffer = ByteArray(maxBytes)
        val read = socket.getInputStream().read(buffer)
        if (read <= 0) byteArrayOf() else buffer.copyOf(read)
    }

    override fun close() {
        socket.close()
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { byte ->
    byte.toUByte().toString(16).uppercase().padStart(2, '0')
}
