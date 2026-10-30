package app.energymonitor.data.auth

import java.io.ByteArrayOutputStream

/**
 * Base64url без доповнення "=" — кодування частин JWT. Власна реалізація
 * замість android.util.Base64, щоб код працював і в звичайних unit-тестах на JVM,
 * де класів Android немає.
 */
object Base64Url {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(bytes: ByteArray): String {
        val result = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 6) {
                bits -= 6
                result.append(ALPHABET[(buffer shr bits) and 0x3F])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) {
            result.append(ALPHABET[(buffer shl (6 - bits)) and 0x3F])
        }
        return result.toString()
    }

    fun decode(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in text) {
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "Недопустимий символ Base64url: $char" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }
}
