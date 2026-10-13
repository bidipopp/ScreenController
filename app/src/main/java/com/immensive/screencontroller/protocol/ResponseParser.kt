package com.immensive.screencontroller.protocol

/**
 * Parse la réponse brute du moniteur en résultat typé.
 *
 * Source note:
 * - "Communication Control / Message-Report"
 * - ACK = 0x00, NACK = 0x03, NAV = 0x04.
 */
object ResponseParser {

    fun parse(raw: ByteArray): CommandResult {
        if (raw.isEmpty()) return CommandResult.Error("Réponse vide")

        if (!checksumValid(raw)) {
            return CommandResult.Error("Checksum invalide dans la réponse")
        }

        // Trame Communication Control (header 0x21), longueur usuelle 9 octets.
        if (raw[0].toUByte() == ProtocolConstants.RESPONSE_HEADER && raw.size >= 9) {
            val command = raw[6].toUByte()
            val status = raw[7].toUByte()

            if (command == ProtocolConstants.COMMUNICATION_CONTROL_COMMAND) {
                return when (status) {
                    ProtocolConstants.STATUS_ACK -> CommandResult.Ack
                    ProtocolConstants.STATUS_NACK -> CommandResult.Nack
                    ProtocolConstants.STATUS_NAV -> CommandResult.Nav
                    else -> CommandResult.Error("Status Communication Control inconnu: 0x${status.toString(16)}")
                }
            }
        }

        return CommandResult.Error("Réponse non supportée en V1")
    }

    private fun checksumValid(raw: ByteArray): Boolean {
        if (raw.size < 2) return false
        val expected = raw.last().toUByte()
        val actual = FrameBuilder.xor(raw.copyOf(raw.size - 1))
        return expected == actual
    }
}
