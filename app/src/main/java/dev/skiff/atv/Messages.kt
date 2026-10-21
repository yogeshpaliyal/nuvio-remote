package dev.skiff.atv

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * The messages of the Android TV Remote protocol (v2), with their field numbers from
 * github.com/tronikos/androidtvremote2 (polo.proto: pairing on port 6467; remotemessage.proto: the remote on 6466).
 */
object Messages {
    // MARK: Pairing (polo.proto: OuterMessage)

    private const val STATUS_OK = 200
    const val ENCODING_HEXADECIMAL = 3
    const val ROLE_INPUT = 1

    private fun outer(body: ProtoWriter.() -> Unit) = ProtoWriter().apply {
        varint(1, 2)            // protocol_version
        varint(2, STATUS_OK)    // status
        body()
    }.toByteArray()

    /** PairingRequest(service_name, client_name): the TV then shows the code. */
    fun pairingRequest(clientName: String) = outer {
        message(10) { string(1, "atvremote"); string(2, clientName) }
    }

    /** Options: we type a 6-character hexadecimal code. */
    fun options() = outer {
        message(20) {
            message(1) { varint(1, ENCODING_HEXADECIMAL); varint(2, 6) }   // input_encodings
            varint(3, ROLE_INPUT)                                          // preferred_role
        }
    }

    /** Configuration: the same encoding, we're the input. */
    fun configuration() = outer {
        message(30) {
            message(1) { varint(1, ENCODING_HEXADECIMAL); varint(2, 6) }   // encoding
            varint(2, ROLE_INPUT)                                          // client_role
        }
    }

    fun secret(secret: ByteArray) = outer { message(40) { bytes(1, secret) } }

    /** An OuterMessage from the TV: its status, and which step it answers. */
    class Outer(bytes: ByteArray) {
        private val r = ProtoReader(bytes)
        val status = r.int(2)
        val isPairingRequestAck = r.has(11)
        val isOptions = r.has(20)
        val isConfigurationAck = r.has(31)
        val isSecretAck = r.has(41)
    }

    /**
     * The secret for a code shown on the TV ("A1B2C3"): SHA-256 over the client's RSA modulus and exponent, the
     * TV's, and the code's last 4 hex digits (2 bytes). The code's first byte is the hash's first byte: a typo check.
     * Null when the code doesn't match these certificates (mistyped).
     */
    fun pairingSecret(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
        val c = code.trim().uppercase()
        if (!Regex("[0-9A-F]{6}").matches(c)) return null
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update(unsigned(client.modulus))
        sha.update(unsigned(client.publicExponent))
        sha.update(unsigned(server.modulus))
        sha.update(unsigned(server.publicExponent))
        sha.update(hex(c.substring(2)))
        val hash = sha.digest()
        return if ((hash[0].toInt() and 0xFF) == c.substring(0, 2).toInt(16)) hash else null
    }

    /** A positive number's big-endian bytes, without the sign byte (Python's bytes.fromhex(f"{n:X}")). */
    fun unsigned(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // MARK: Remote (remotemessage.proto: RemoteMessage)

    // Features (RemoteConfigure.code1, RemoteSetActive.active)
    const val FEATURE_PING = 1
    const val FEATURE_KEY = 2
    const val FEATURE_IME = 4
    const val FEATURE_POWER = 32
    const val FEATURE_VOLUME = 64
    const val FEATURE_APP_LINK = 512
    const val FEATURES = FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK

    // Key directions: a press, or held down (a modifier: Shift) and let go.
    const val DIRECTION_DOWN = 1
    const val DIRECTION_UP = 2
    const val DIRECTION_SHORT = 3

    /** Our RemoteConfigure: the features we use (those the TV supports), and who we are (as androidtvremote2 sends it). */
    fun remoteConfigure(features: Int) = ProtoWriter().message(1) {
        varint(1, features)
        message(2) {
            varint(3, 1)                // unknown1
            string(4, "1")              // unknown2
            string(5, "dev.skiff")      // package_name
            string(6, "1.0.0")          // app_version
        }
    }.toByteArray()

    fun remoteSetActive(features: Int) = ProtoWriter().message(2) { varint(1, features) }.toByteArray()

    fun pingResponse(val1: Int) = ProtoWriter().message(9) { varint(1, val1) }.toByteArray()

    /** A key press: the key codes are Android's own (KeyEvent.KEYCODE_*). */
    fun keyInject(keyCode: Int, direction: Int = DIRECTION_SHORT) =
        ProtoWriter().message(10) { varint(1, keyCode); varint(2, direction) }.toByteArray()

    /** Opens a link on the TV, in the app that opens it (an app's own link, a web link, market://launch?id=…). */
    fun appLink(link: String) = ProtoWriter().message(90) { string(1, link) }.toByteArray()

    /** Text into the TV's focused field, by its input method (it sent its counters when the field got focus). */
    fun imeText(imeCounter: Int, fieldCounter: Int, text: String) = ProtoWriter().message(21) {
        varint(1, imeCounter)
        varint(2, fieldCounter)
        message(3) {
            varint(1, 1)    // insert
            message(2) { varint(1, text.length - 1); varint(2, text.length - 1); string(3, text) }
        }
    }.toByteArray()

    /** A RemoteMessage from the TV. */
    class Remote(bytes: ByteArray) {
        private val r = ProtoReader(bytes)
        val configure = r.message(1)
        val setActive = r.has(2)
        val error = r.has(3)
        val pingVal1: Int? = r.message(8)?.int(1)
        val imeBatchEdit = r.message(21)
        /** The focused text field's counter, as the TV announces it (key inject 20, show request 22). */
        val imeFieldCounter: Int? = (r.message(20) ?: r.message(22))?.message(2)?.takeIf { it.has(1) }?.int(1)
        val start = r.message(40)
        val fields = r.fields
    }
}
