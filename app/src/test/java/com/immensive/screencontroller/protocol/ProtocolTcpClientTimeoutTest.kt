package com.immensive.screencontroller.protocol

import java.net.ServerSocket
import java.net.UnknownHostException
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolTcpClientTimeoutTest {

    private val client = ProtocolTcpClient()

    @Test
    fun `returns ACK when server sends ACK`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.ACK).use { server ->
            val result = client.send(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 500,
            )

            assertEquals(CommandResult.Ack, result)
        }
    }

    @Test
    fun `returns NACK when server sends NACK`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.NACK).use { server ->
            val result = client.send(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 500,
            )

            assertEquals(CommandResult.Nack, result)
        }
    }

    @Test
    fun `returns NAV when server sends NAV`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.NAV).use { server ->
            val result = client.send(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 500,
            )

            assertEquals(CommandResult.Nav, result)
        }
    }

    @Test
    fun `returns timeout when server does not respond`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.NO_RESPONSE).use { server ->
            val result = client.send(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 100,
            )

            assertEquals(CommandResult.Timeout, result)
        }
    }

    @Test
    fun `returns connection closed when server closes connection`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.CLOSE_CONNECTION).use { server ->
            val result = client.send(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 500,
            )

            assertTrue(result is CommandResult.ConnectionClosed)
        }
    }

    @Test
    fun `returns connection refused when no server listens on port`() = runTest {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val result = client.send(
            host = "127.0.0.1",
            command = ResetCommand,
            port = unusedPort,
            timeoutMs = 500,
        )

        assertTrue(result is CommandResult.ConnectionRefused)
    }

    @Test
    fun `returns unknown host when host cannot be resolved`() = runTest {
        val expected = UnknownHostException("dns failure")
        val customClient = ProtocolTcpClient(
            connectionFactory = object : TcpConnectionFactory {
                override suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection {
                    throw expected
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = customClient.send(
            host = "irrelevant",
            command = ResetCommand,
            timeoutMs = 500,
        )

        assertTrue(result is CommandResult.UnknownHost)
        assertEquals(expected, (result as CommandResult.UnknownHost).cause)
    }

    @Test
    fun `returns generic error for unexpected throwable`() = runTest {
        val expected = IllegalStateException("boom")
        val customClient = ProtocolTcpClient(
            connectionFactory = object : TcpConnectionFactory {
                override suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection {
                    throw expected
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = customClient.send(
            host = "127.0.0.1",
            command = ResetCommand,
            timeoutMs = 500,
        )

        assertTrue(result is CommandResult.Error)
        assertEquals(expected, (result as CommandResult.Error).cause)
    }

    @Test
    fun `propagates coroutine cancellation`() = runTest {
        val cancellationAwareClient = ProtocolTcpClient(
            connectionFactory = object : TcpConnectionFactory {
                override suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection {
                    return object : TcpConnection {
                        override suspend fun write(data: ByteArray) = Unit
                        override suspend fun read(maxBytes: Int): ByteArray {
                            delay(5_000)
                            return byteArrayOf(0x00)
                        }

                        override fun close() = Unit
                    }
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val deferred = async {
            cancellationAwareClient.send(
                host = "127.0.0.1",
                command = ResetCommand,
                timeoutMs = 10_000,
            )
        }

        val canceller = launch {
            delay(50)
            deferred.cancel()
        }

        assertFailsWith<CancellationException> {
            deferred.await()
        }
        canceller.cancelAndJoin()
    }
}
