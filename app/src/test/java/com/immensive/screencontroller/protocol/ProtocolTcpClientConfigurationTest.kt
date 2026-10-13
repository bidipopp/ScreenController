package com.immensive.screencontroller.protocol

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolTcpClientConfigurationTest {
    @Test
    fun `default timeout is higher than protocol retry hint`() {
        assertTrue(ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS > 500L)
    }

    @Test
    fun `uses default timeout from protocol constants when caller omits timeout`() = runTest {
        val recorder = RecordingConnectionFactory()
        val client = ProtocolTcpClient(
            connectionFactory = recorder,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = client.send(
            host = "127.0.0.1",
            command = ResetCommand,
        )

        assertEquals(CommandResult.Ack, result)
        assertEquals(ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS.toInt(), recorder.lastTimeoutMs)
    }

    @Test
    fun `forwards explicit timeout to connection factory`() = runTest {
        val recorder = RecordingConnectionFactory()
        val client = ProtocolTcpClient(
            connectionFactory = recorder,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = client.send(
            host = "127.0.0.1",
            command = ResetCommand,
            timeoutMs = 4_500L,
        )

        assertEquals(CommandResult.Ack, result)
        assertEquals(4_500, recorder.lastTimeoutMs)
    }

    @Test
    fun `rejects timeout lower than one millisecond`() = runTest {
        val recorder = RecordingConnectionFactory()
        val client = ProtocolTcpClient(
            connectionFactory = recorder,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = client.send(
            host = "127.0.0.1",
            command = ResetCommand,
            timeoutMs = 0,
        )

        assertTrue(result is CommandResult.Error)
        assertEquals(null, recorder.lastTimeoutMs)
    }

    @Test
    fun `rejects timeout larger than Int max value`() = runTest {
        val recorder = RecordingConnectionFactory()
        val client = ProtocolTcpClient(
            connectionFactory = recorder,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = client.send(
            host = "127.0.0.1",
            command = ResetCommand,
            timeoutMs = Int.MAX_VALUE.toLong() + 1L,
        )

        assertTrue(result is CommandResult.Error)
        assertEquals(null, recorder.lastTimeoutMs)
    }

    private class RecordingConnectionFactory : TcpConnectionFactory {
        var lastTimeoutMs: Int? = null

        override suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection {
            lastTimeoutMs = timeoutMs
            return object : TcpConnection {
                override suspend fun write(data: ByteArray) = Unit
                override suspend fun read(maxBytes: Int): ByteArray = ackFrame()
                override fun close() = Unit
            }
        }

        private fun ackFrame(): ByteArray {
            val frame = mutableListOf<UByte>()
            frame += ProtocolConstants.RESPONSE_HEADER
            frame += ProtocolConstants.DEFAULT_MONITOR_ID
            frame += ProtocolConstants.CATEGORY
            frame += ProtocolConstants.CODE0
            frame += 0x04u
            frame += ProtocolConstants.DATA_CONTROL
            frame += ProtocolConstants.COMMUNICATION_CONTROL_COMMAND
            frame += ProtocolConstants.STATUS_ACK
            frame += FrameBuilder.xor(frame)
            return frame.map { it.toByte() }.toByteArray()
        }
    }
}
