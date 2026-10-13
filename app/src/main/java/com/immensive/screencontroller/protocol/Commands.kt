package com.immensive.screencontroller.protocol

/**
 * Représente une commande protocole, sérialisable en trame.
 */
interface Command {
    val code: UByte
    val payload: ByteArray

    fun toFrame(monitorId: UByte = ProtocolConstants.DEFAULT_MONITOR_ID): ByteArray =
        FrameBuilder.build(command = code, payload = payload, monitorId = monitorId)
}

/**
 * Commande de reset (vecteur de test connu de la note):
 * A6 01 00 00 00 03 01 01 A4
 *
 * Source note:
 * - section "Command Format" + exemple de reset fourni dans la documentation projet.
 */
data object ResetCommand : Command {
    override val code: UByte = 0x01u
    override val payload: ByteArray = byteArrayOf()
}

/**
 * MESSAGES - GENERAL / Power state.
 *
 * Codes:
 * - Set: 0x18
 * - Get: 0x19
 */
sealed class PowerCommand : Command {
    data object Get : PowerCommand() {
        override val code: UByte = 0x19u
        override val payload: ByteArray = byteArrayOf()
    }

    data class Set(val state: PowerState) : PowerCommand() {
        override val code: UByte = 0x18u
        override val payload: ByteArray = byteArrayOf(state.value.toByte())
    }
}

/**
 * Valeurs décrites dans "Power state".
 *
 * TODO(vérifier doc): la section "Notes about powering ON the screen via LAN"
 * est partiellement illisible dans l'extraction; vérifier le prérequis exact
 * ("Power Save ... Mode 2") avant validation matériel.
 */
enum class PowerState(val value: UByte) {
    OFF(0x01u),
    ON(0x02u),
}

/**
 * MESSAGES - GENERAL / Lock Functions for IR-Remote Control.
 *
 * Codes:
 * - Set: 0x1C
 * - Get: 0x1D
 */
sealed class UserInputControlCommand : Command {
    data object Get : UserInputControlCommand() {
        override val code: UByte = 0x1Du
        override val payload: ByteArray = byteArrayOf()
    }

    data class Set(val mode: IrRemoteLockMode) : UserInputControlCommand() {
        override val code: UByte = 0x1Cu
        override val payload: ByteArray = byteArrayOf(mode.value.toByte())
    }
}

/**
 * Modes indiqués dans la section IR-Remote Control (DATA[1]).
 */
enum class IrRemoteLockMode(val value: UByte) {
    UNLOCK_ALL(0x01u),
    LOCK_ALL(0x02u),
    LOCK_ALL_BUT_POWER(0x03u),
    LOCK_ALL_BUT_VOLUME(0x04u),
    PRIMARY_MASTER(0x05u),
    SECONDARY_DAISY_CHAIN(0x06u),
    LOCK_ALL_EXCEPT_POWER_AND_VOLUME(0x07u),
}

/**
 * MESSAGES - COLOR / Color Temperature.
 *
 * Codes:
 * - Set: 0x34
 * - Get: 0x35
 */
sealed class ColorTemperatureCommand : Command {
    data object Get : ColorTemperatureCommand() {
        override val code: UByte = 0x35u
        override val payload: ByteArray = byteArrayOf()
    }

    data class Set(val value: ColorTemperatureValue) : ColorTemperatureCommand() {
        override val code: UByte = 0x34u
        override val payload: ByteArray = byteArrayOf(value.value.toByte())
    }
}

/**
 * Valeurs de température de couleur décrites dans la note.
 * Certaines sont notées "Not applicable".
 */
enum class ColorTemperatureValue(val value: UByte) {
    USER_1(0x00u),
    NATIVE(0x01u),
    K11000_NOT_APPLICABLE(0x02u),
    K10000(0x03u),
    K9300(0x04u),
    K7500(0x05u),
    K6500(0x06u),
    K5770_NOT_APPLICABLE(0x07u),
    K5500_NOT_APPLICABLE(0x08u),
    K5000(0x09u),
    K4000(0x0Au),
    K3400_NOT_APPLICABLE(0x0Bu),
    K3350_NOT_APPLICABLE(0x0Cu),
    K3000(0x0Du),
    K2800_NOT_APPLICABLE(0x0Eu),
    K2600_NOT_APPLICABLE(0x0Fu),
    K1850_NOT_APPLICABLE(0x10u),
    USER_2(0x12u),
}

/**
 * MESSAGES - COLOR / Color Parameters (gain/offset RGB).
 *
 * Codes:
 * - Set: 0x36
 * - Get: 0x37
 */
sealed class ColorParametersCommand : Command {
    data object Get : ColorParametersCommand() {
        override val code: UByte = 0x37u
        override val payload: ByteArray = byteArrayOf()
    }

    data class Set(val values: ColorParameters) : ColorParametersCommand() {
        override val code: UByte = 0x36u
        override val payload: ByteArray = values.toPayload()
    }
}

/**
 * Paramètres couleur sur 0..255 selon la note.
 */
data class ColorParameters(
    val redGain: UByte,
    val greenGain: UByte,
    val blueGain: UByte,
    val redOffset: UByte,
    val greenOffset: UByte,
    val blueOffset: UByte,
) {
    fun toPayload(): ByteArray = byteArrayOf(
        redGain.toByte(),
        greenGain.toByte(),
        blueGain.toByte(),
        redOffset.toByte(),
        greenOffset.toByte(),
        blueOffset.toByte(),
    )
}

/**
 * MESSAGES - VIDEO / Video Parameters.
 *
 * Codes:
 * - Set: 0x32
 * - Get: 0x33
 */
sealed class VideoParametersCommand : Command {
    data object Get : VideoParametersCommand() {
        override val code: UByte = 0x33u
        override val payload: ByteArray = byteArrayOf()
    }

    data class Set(val values: VideoParameters) : VideoParametersCommand() {
        override val code: UByte = 0x32u
        override val payload: ByteArray = values.toPayload()
    }
}

/**
 * Paramètres vidéo selon la note.
 * DATA[1..6] : 0..100, DATA[7] : gamma 0x01..0x05.
 */
data class VideoParameters(
    val brightness: UByte,
    val color: UByte,
    val contrast: UByte,
    val sharpness: UByte,
    val tint: UByte,
    val blackLevel: UByte,
    val gamma: GammaSelection,
) {
    init {
        require(brightness.toInt() in 0..100) { "brightness hors plage 0..100" }
        require(color.toInt() in 0..100) { "color hors plage 0..100" }
        require(contrast.toInt() in 0..100) { "contrast hors plage 0..100" }
        require(sharpness.toInt() in 0..100) { "sharpness hors plage 0..100" }
        require(tint.toInt() in 0..100) { "tint hors plage 0..100" }
        require(blackLevel.toInt() in 0..100) { "blackLevel hors plage 0..100" }
    }

    fun toPayload(): ByteArray = byteArrayOf(
        brightness.toByte(),
        color.toByte(),
        contrast.toByte(),
        sharpness.toByte(),
        tint.toByte(),
        blackLevel.toByte(),
        gamma.value.toByte(),
    )
}

/**
 * Valeurs gamma de la section Video Parameters.
 */
enum class GammaSelection(val value: UByte) {
    NATIVE(0x01u),
    S_GAMMA(0x02u),
    GAMMA_22(0x03u),
    GAMMA_24(0x04u),
    DICOM(0x05u),
}
