package com.immensive.screencontroller.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseParserTest {

    @Test
    fun `parse ACK response`() {
        val ack = byteArrayOf(
            0x21,
            0x01,
            0x00,
            0x00,
            0x04,
            0x01,
            0x00,
            0x00,
            0x25,
        )

        assertEquals(CommandResult.Ack, ResponseParser.parse(ack))
    }

    @Test
    fun `parse NACK response`() {
        val nack = byteArrayOf(
            0x21,
            0x01,
            0x00,
            0x00,
            0x04,
            0x01,
            0x00,
            0x03,
            0x26,
        )

        assertEquals(CommandResult.Nack, ResponseParser.parse(nack))
    }

    @Test
    fun `parse NAV response`() {
        val nav = byteArrayOf(
            0x21,
            0x01,
            0x00,
            0x00,
            0x04,
            0x01,
            0x00,
            0x04,
            0x21,
        )

        assertEquals(CommandResult.Nav, ResponseParser.parse(nav))
    }
}
