package com.immensive.screencontroller.protocol

/**
 * Construit une trame RS232/LAN iiyama.
 *
 * Source note:
 * - "Command Format" (Header, Monitor ID, Category, Code0, Code1, Length, Data Control, Data..., Checksum)
 * - "Algorithm: XOR of all bytes except checksum".
 */
object FrameBuilder {

    fun build(
        command: UByte,
        payload: ByteArray = byteArrayOf(),
        monitorId: UByte = ProtocolConstants.DEFAULT_MONITOR_ID
    ): ByteArray {
        val raw = mutableListOf<UByte>()
        raw += ProtocolConstants.HEADER
        raw += monitorId
        raw += ProtocolConstants.CATEGORY
        raw += ProtocolConstants.CODE0
        raw += ProtocolConstants.CODE1

        // Length = bytes from Data Control to Checksum inclus.
        // Ici, sans checksum: DataControl(1) + Command/Payload(1 + payload.size)
        val lengthWithoutChecksum = 1 + 1 + payload.size
        val length = (lengthWithoutChecksum + 1).toUByte() // + Checksum
        raw += length
        raw += ProtocolConstants.DATA_CONTROL
        raw += command
        payload.forEach { raw += it.toUByte() }

        val checksum = xor(raw)
        raw += checksum
        return raw.map { it.toByte() }.toByteArray()
    }

    fun xor(bytes: Collection<UByte>): UByte {
        var result = 0u
        for (byte in bytes) {
            result = result xor byte.toUInt()
        }
        return result.toUByte()
    }

    fun xor(bytes: ByteArray): UByte = xor(bytes.map { it.toUByte() })
}
