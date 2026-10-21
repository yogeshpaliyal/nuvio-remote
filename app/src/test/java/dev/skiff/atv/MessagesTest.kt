package dev.skiff.atv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

/**
 * The protocol's bytes, checked without a TV: the pairing secret against androidtvremote2's formula (computed with
 * its Python code), and messages against the protobuf encoding (worked out by hand).
 */
class MessagesTest {
    private fun rsa(n: BigInteger): RSAPublicKey =
        KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, BigInteger.valueOf(65537))) as RSAPublicKey

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val client = rsa(BigInteger.ONE.shiftLeft(2047).add(BigInteger.valueOf(123456789)))
    private val server = rsa(BigInteger.ONE.shiftLeft(2047).add(BigInteger.valueOf(987654321)))

    @Test fun pairingSecretMatchesThePythonImplementation() {
        // python: sha256(n1 ‖ e ‖ n2 ‖ e ‖ bytes.fromhex("ABCD")), code = f"{digest[0]:02X}ABCD"
        val expected = hex("598257ac4bf5247e2ac604016a290cd0b33a97646b6df08064d4360358ee4003")
        assertArrayEquals(expected, Messages.pairingSecret(client, server, "59ABCD"))
        assertArrayEquals(expected, Messages.pairingSecret(client, server, "59abcd"))   // as typed, lowercase
    }

    @Test fun mistypedCodeIsRejected() {
        assertNull(Messages.pairingSecret(client, server, "58ABCD"))    // the check byte doesn't match
        assertNull(Messages.pairingSecret(client, server, "59ABC"))     // too short
        assertNull(Messages.pairingSecret(client, server, "59ABCG"))    // not hex
    }

    @Test fun unsignedDropsTheSignByteOnly() {
        assertArrayEquals(byteArrayOf(1, 0, 1), Messages.unsigned(BigInteger.valueOf(65537)))
        assertEquals(256, Messages.unsigned(client.modulus).size)
    }

    @Test fun keyInjectBytes() {
        // RemoteMessage { remote_key_inject (10) { key_code (1): 19, direction (2): SHORT = 3 } }
        assertArrayEquals(hex("520408131003"), Messages.keyInject(19))
    }

    @Test fun shiftHeldBytes() {
        // remote_key_inject { key_code: 59 (SHIFT_LEFT), direction: START_LONG = 1 }
        assertArrayEquals(hex("5204083b1001"), Messages.keyInject(59, Messages.DIRECTION_DOWN))
    }

    @Test fun readsTheFocusedFieldCounter() {
        // RemoteMessage { remote_ime_show_request (22) { remote_text_field_status (2) { counter_field (1): 5 } } }
        val show = ProtoWriter().message(22) { message(2) { varint(1, 5) } }.toByteArray()
        assertEquals(5, Messages.Remote(show).imeFieldCounter)
    }

    @Test fun readsTheFocusedFieldsCounters() {
        // What a Google TV sent when Nuvio's search box had focus: app_info.counter 260, counter_field 60.
        val inject = hex("a201350a270884021001188680801038004000620c636f6d2e6e7576696f2e747668ffffffffffffffffff01120a083c1200180020002801")
        val m = Messages.Remote(inject)
        assertEquals(260, m.imeAppCounter)
        assertEquals(60, m.imeFieldCounter)
    }

    @Test fun appLinkBytes() {
        // RemoteMessage { remote_app_link_launch_request (90) { app_link (1): "x" } }: tag 90<<3|2 = 722 = D2 05
        assertArrayEquals(hex("d205030a0178"), Messages.appLink("x"))
    }

    @Test fun pairingMessagesStartWithVersionAndStatus() {
        // OuterMessage { protocol_version (1): 2, status (2): 200 = C8 01, … }
        val m = Messages.pairingRequest("Skiff")
        assertArrayEquals(hex("080210c801"), m.copyOfRange(0, 5))
        val read = Messages.Outer(m)
        assertEquals(200, read.status)
    }

    @Test fun framesRoundTrip() {
        val out = ByteArrayOutputStream()
        val big = ByteArray(300) { it.toByte() }        // a two-byte varint length
        ProtoWriter.frame(out, Messages.keyInject(23))
        ProtoWriter.frame(out, big)
        val bytes = out.toByteArray()
        assertArrayEquals(hex("06520408171003"), bytes.copyOfRange(0, 7))
        val input = ByteArrayInputStream(bytes)
        assertArrayEquals(Messages.keyInject(23), ProtoReader.readFrame(input))
        assertArrayEquals(big, ProtoReader.readFrame(input))
    }

    @Test fun readsTheTvsMessages() {
        // RemoteMessage { remote_ping_request (8) { val1: 42 } }
        val ping = ProtoWriter().message(8) { varint(1, 42) }.toByteArray()
        assertEquals(42, Messages.Remote(ping).pingVal1)
        // RemoteMessage { remote_ime_batch_edit (21) { ime_counter: 7, field_counter: 3 } }
        val ime = ProtoWriter().message(21) { varint(1, 7); varint(2, 3) }.toByteArray()
        val edit = Messages.Remote(ime).imeBatchEdit!!
        assertEquals(7, edit.int(1))
        assertEquals(3, edit.int(2))
    }
}
