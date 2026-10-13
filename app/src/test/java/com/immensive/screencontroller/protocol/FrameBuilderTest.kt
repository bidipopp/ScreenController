package com.immensive.screencontroller.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FrameBuilderTest {

    @Test
    fun `reset frame matches known vector`() {
        val frame = ResetCommand.toFrame()

        val expected = byteArrayOf(
            0xA6.toByte(),
            0x01,
            0x00,
            0x00,
            0x00,
            0x03,
            0x01,
            0x01,
            0xA4.toByte(),
        )

        assertContentEquals(expected, frame)
    }

    @Test
    fun `xor checksum is correct for reset frame without checksum`() {
        val withoutChecksum = byteArrayOf(
            0xA6.toByte(),
            0x01,
            0x00,
            0x00,
            0x00,
            0x03,
            0x01,
            0x01,
        )

        val checksum = FrameBuilder.xor(withoutChecksum)
        assertEquals(0xA4u, checksum)
    }
}
