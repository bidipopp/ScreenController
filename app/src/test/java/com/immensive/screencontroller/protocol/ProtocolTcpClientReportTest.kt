package com.immensive.screencontroller.protocol

import java.net.SocketTimeoutException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProtocolTcpClientReportTest {

    @Test
    fun `sendWithReport exposes all successful steps for ACK`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.ACK).use { server ->
            val client = ProtocolTcpClient()

            val report = client.sendWithReport(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 500,
            )

            assertEquals(CommandResult.Ack, report.commandResult)
            assertEquals(500L, report.timeoutMs)
            assertTrue(report.totalDurationMs >= 0)
            assertTrue(report.txHex.isNotBlank())
            assertNotNull(report.rxHex)
            assertEquals(
                listOf(
                    CommandExecutionStep.CONNECT,
                    CommandExecutionStep.WRITE,
                    CommandExecutionStep.READ,
                    CommandExecutionStep.PARSE,
                ),
                report.steps.map { it.step }
            )
            assertTrue(report.steps.all { it.status == CommandExecutionStepStatus.OK })
        }
    }

    @Test
    fun `sendWithReport marks READ as failed on response timeout`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.NO_RESPONSE).use { server ->
            val client = ProtocolTcpClient()

            val report = client.sendWithReport(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 100,
            )

            assertEquals(CommandResult.Timeout, report.commandResult)
            val readStep = report.steps.last()
            assertEquals(CommandExecutionStep.READ, readStep.step)
            assertEquals(CommandExecutionStepStatus.FAIL, readStep.status)
            assertTrue(readStep.message.contains("Timeout"))
        }
    }

    @Test
    fun `sendWithReport marks READ as failed when connection is closed without response`() = runTest {
        FakeProtocolTcpServer(FakeProtocolTcpServer.Behavior.CLOSE_CONNECTION).use { server ->
            val client = ProtocolTcpClient()

            val report = client.sendWithReport(
                host = "127.0.0.1",
                command = ResetCommand,
                port = server.port,
                timeoutMs = 500,
            )

            assertEquals(CommandResult.ConnectionClosed(), report.commandResult)
            val readStep = report.steps.last()
            assertEquals(CommandExecutionStep.READ, readStep.step)
            assertEquals(CommandExecutionStepStatus.FAIL, readStep.status)
            assertEquals(null, report.rxHex)
        }
    }

    @Test
    fun `send still returns same business result as sendWithReport`() = runTest {
        val client = ProtocolTcpClient(
            connectionFactory = object : TcpConnectionFactory {
                override suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection {
                    return object : TcpConnection {
                        override suspend fun write(data: ByteArray) = Unit
                        override suspend fun read(maxBytes: Int): ByteArray {
                            throw SocketTimeoutException("simulated read timeout")
                        }

                        override fun close() = Unit
                    }
                }
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val sendResult = client.send(
            host = "127.0.0.1",
            command = ResetCommand,
            timeoutMs = 250,
        )

        val reportResult = client.sendWithReport(
            host = "127.0.0.1",
            command = ResetCommand,
            timeoutMs = 250,
        ).commandResult

        assertEquals(sendResult, reportResult)
        assertEquals(CommandResult.Timeout, reportResult)
    }
}
