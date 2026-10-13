package com.immensive.screencontroller.ui

import com.immensive.screencontroller.data.MonitorCommandRepository
import com.immensive.screencontroller.protocol.FrameBuilder
import com.immensive.screencontroller.protocol.ProtocolConstants
import com.immensive.screencontroller.protocol.ProtocolTcpClient
import com.immensive.screencontroller.protocol.TcpConnection
import com.immensive.screencontroller.protocol.TcpConnectionFactory
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TestControllerViewModelTest {
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rejects timeout lower or equal to zero`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        val viewModel = TestControllerViewModel()
        viewModel.updateHost("127.0.0.1")
        viewModel.updatePort("timeout:0")

        viewModel.send(TestAction.POWER_ON)

        assertEquals("Timeout invalide (1..2147483647 ms)", viewModel.uiState.inputError)
        assertEquals(
            "Entrée invalide: Timeout invalide (1..2147483647 ms)",
            viewModel.uiState.diagnostic,
        )
    }

    @Test
    fun `rejects timeout above Int max value`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        val viewModel = TestControllerViewModel()
        viewModel.updateHost("127.0.0.1")
        viewModel.updatePort("timeout:${Int.MAX_VALUE.toLong() + 1L}")

        viewModel.send(TestAction.POWER_ON)

        assertEquals("Timeout invalide (1..2147483647 ms)", viewModel.uiState.inputError)
    }

    @Test
    fun `propagates valid timeout to repository client`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        val recorder = RecordingConnectionFactory { ackFrame() }
        val repository = MonitorCommandRepository(
            client = ProtocolTcpClient(
                connectionFactory = recorder,
                ioDispatcher = mainDispatcher,
            ),
        )
        val viewModel = TestControllerViewModel(repository)
        viewModel.updateHost("127.0.0.1")
        viewModel.updatePort("timeout:4321")

        viewModel.send(TestAction.POWER_ON)
        advanceUntilIdle()

        assertNull(viewModel.uiState.inputError)
        assertEquals(4_321, recorder.lastTimeoutMs)
    }

    @Test
    fun `formats timeout result message with connection or response wording`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        val timeoutFactory = RecordingConnectionFactory { throw SocketTimeoutException("read timeout") }
        val repository = MonitorCommandRepository(
            client = ProtocolTcpClient(
                connectionFactory = timeoutFactory,
                ioDispatcher = mainDispatcher,
            ),
        )
        val viewModel = TestControllerViewModel(repository)
        viewModel.updateHost("127.0.0.1")
        viewModel.updatePort("timeout:250")

        viewModel.send(TestAction.POWER_ON)
        advanceUntilIdle()

        assertEquals("Timeout après 250 ms (connexion ou réponse)", viewModel.uiState.lastResult)
        assertContains(viewModel.uiState.diagnostic, "Timeout configuré: 250 ms")
        assertContains(viewModel.uiState.diagnostic, "RX: n/a")
        assertContains(viewModel.uiState.diagnostic, "READ | FAIL")
    }

    @Test
    fun `includes detailed diagnostic when command is acknowledged`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)
        val repository = MonitorCommandRepository(
            client = ProtocolTcpClient(
                connectionFactory = RecordingConnectionFactory { ackFrame() },
                ioDispatcher = mainDispatcher,
            ),
        )
        val viewModel = TestControllerViewModel(repository)
        viewModel.updateHost("127.0.0.1")
        viewModel.updatePort("timeout:1500")

        viewModel.send(TestAction.POWER_ON)
        advanceUntilIdle()

        assertEquals("ACK (succès)", viewModel.uiState.lastResult)
        assertContains(viewModel.uiState.diagnostic, "Timeout configuré: 1500 ms")
        assertContains(viewModel.uiState.diagnostic, "TX:")
        assertContains(viewModel.uiState.diagnostic, "RX:")
        assertContains(viewModel.uiState.diagnostic, "CONNECT | OK")
        assertContains(viewModel.uiState.diagnostic, "PARSE | OK")
    }

    private class RecordingConnectionFactory(
        private val readBlock: suspend () -> ByteArray,
    ) : TcpConnectionFactory {
        var lastTimeoutMs: Int? = null

        override suspend fun connect(host: String, port: Int, timeoutMs: Int): TcpConnection {
            lastTimeoutMs = timeoutMs
            return object : TcpConnection {
                override suspend fun write(data: ByteArray) = Unit
                override suspend fun read(maxBytes: Int): ByteArray = readBlock()
                override fun close() = Unit
            }
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
