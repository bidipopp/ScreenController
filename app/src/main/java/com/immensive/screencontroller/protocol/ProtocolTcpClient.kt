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
    ): CommandResult = withContext(ioDispatcher) {
        if (timeoutMs !in 1L..MAX_TIMEOUT_MS) {
            val message = "Timeout invalide: $timeoutMs ms (attendu: 1..$MAX_TIMEOUT_MS)"
            return@withContext CommandResult.Error(message, IllegalArgumentException(message))
        }

        try {
            withTimeout(timeoutMs) {
                connectionFactory.connect(host, port, timeoutMs.toInt()).use { connection ->
                    connection.write(command.toFrame(monitorId))
                    val response = connection.read()
                    if (response.isEmpty()) {
                        CommandResult.ConnectionClosed()
                    } else {
                        ResponseParser.parse(response)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            CommandResult.Timeout
        } catch (_: SocketTimeoutException) {
            CommandResult.Timeout
        } catch (e: ConnectException) {
            CommandResult.ConnectionRefused(e)
        } catch (e: UnknownHostException) {
            CommandResult.UnknownHost(e)
        } catch (e: SocketException) {
            CommandResult.ConnectionClosed(e)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            CommandResult.Error("Erreur TCP", t)
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
