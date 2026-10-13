package com.immensive.screencontroller.protocol

/**
 * Constantes de trame selon la section "Command Format" de la note RS232/LAN.
 */
object ProtocolConstants {
    const val HEADER: UByte = 0xA6u
    const val DEFAULT_MONITOR_ID: UByte = 0x01u
    const val CATEGORY: UByte = 0x00u
    const val CODE0: UByte = 0x00u
    const val CODE1: UByte = 0x00u
    const val DATA_CONTROL: UByte = 0x01u

    const val LAN_TCP_PORT: Int = 5000

    const val RESPONSE_HEADER: UByte = 0x21u
    const val COMMUNICATION_CONTROL_COMMAND: UByte = 0x00u
    const val STATUS_ACK: UByte = 0x00u
    const val STATUS_NACK: UByte = 0x03u
    const val STATUS_NAV: UByte = 0x04u
}
