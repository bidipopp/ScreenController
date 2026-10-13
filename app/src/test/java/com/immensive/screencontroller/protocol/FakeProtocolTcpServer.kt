package com.immensive.screencontroller.protocol

import java.io.Closeable
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Faux serveur TCP pour tests d'intégration locale du client protocole.
 */
class FakeProtocolTcpServer(
    private val behavior: Behavior,
) : Closeable {

    enum class Behavior {
        ACK,
        NACK,
        NAV,
        NO_RESPONSE,
        CLOSE_CONNECTION,
    }

    private val serverSocket = ServerSocket(0)
    private val started = CountDownLatch(1)
    private val worker = thread(start = true, isDaemon = true, name = "fake-protocol-tcp-server") {
        started.countDown()
        try {
            serverSocket.accept().use { client ->
                when (behavior) {
                    Behavior.ACK -> {
                        consumeRequest(client)
                        client.getOutputStream().write(communicationControlResponse(ProtocolConstants.STATUS_ACK))
                        client.getOutputStream().flush()
                    }

                    Behavior.NACK -> {
                        consumeRequest(client)
                        client.getOutputStream().write(communicationControlResponse(ProtocolConstants.STATUS_NACK))
                        client.getOutputStream().flush()
                    }

                    Behavior.NAV -> {
                        consumeRequest(client)
                        client.getOutputStream().write(communicationControlResponse(ProtocolConstants.STATUS_NAV))
                        client.getOutputStream().flush()
                    }

                    Behavior.NO_RESPONSE -> {
                        consumeRequest(client)
                        Thread.sleep(1_000)
                    }

                    Behavior.CLOSE_CONNECTION -> {
                        // Fermeture immédiate pour simuler une coupure distante.
                    }
                }
            }
        } catch (_: SocketException) {
            // Fermeture attendue lors du close() du serveur.
        }
    }

    val port: Int = serverSocket.localPort

    init {
        check(started.await(1, TimeUnit.SECONDS)) { "Le faux serveur TCP n'a pas démarré" }
    }

    override fun close() {
        serverSocket.close()
        worker.join(1_000)
    }

    private fun consumeRequest(socket: java.net.Socket) {
        socket.getInputStream().read(ByteArray(256))
    }

    private fun communicationControlResponse(status: UByte): ByteArray {
        val frame = mutableListOf<UByte>()
        frame += ProtocolConstants.RESPONSE_HEADER
        frame += ProtocolConstants.DEFAULT_MONITOR_ID
        frame += ProtocolConstants.CATEGORY
        frame += ProtocolConstants.CODE0
        frame += 0x04u
        frame += ProtocolConstants.DATA_CONTROL
        frame += ProtocolConstants.COMMUNICATION_CONTROL_COMMAND
        frame += status
        frame += FrameBuilder.xor(frame)
        return frame.map { it.toByte() }.toByteArray()
    }
}
