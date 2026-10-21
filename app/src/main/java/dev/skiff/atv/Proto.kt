package dev.skiff.atv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * The little protobuf the Android TV Remote protocol needs (polo.proto and remotemessage.proto in
 * github.com/tronikos/androidtvremote2): varints, strings, bytes and nested messages, framed by a varint length.
 * No library: a handful of messages, each built by hand.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter {
        tag(field, 0)
        writeVarint(out, value)
        return this
    }

    fun varint(field: Int, value: Int) = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean) = varint(field, if (value) 1 else 0)

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        tag(field, 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())

    companion object {
        fun writeVarint(out: OutputStream, value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }

        /** One message on the wire: its length as a varint, then its bytes. */
        fun frame(out: OutputStream, message: ByteArray) {
            val b = ByteArrayOutputStream()
            writeVarint(b, message.size.toLong())
            b.write(message)
            out.write(b.toByteArray())     // one write: one TLS record per message
            out.flush()
        }
    }
}

/** The fields of one message: varints and length-delimited values, by field number (the last one wins). */
class ProtoReader(bytes: ByteArray) {
    private val varints = HashMap<Int, Long>()
    private val blobs = HashMap<Int, ByteArray>()

    init {
        var i = 0
        fun readVarint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                if (i >= bytes.size) throw EOFException("truncated varint")
                val b = bytes[i++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 63) throw IllegalStateException("varint too long")
            }
        }
        while (i < bytes.size) {
            val key = readVarint()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> varints[field] = readVarint()
                2 -> {
                    val len = readVarint().toInt()
                    if (len < 0 || i + len > bytes.size) throw EOFException("truncated field $field")
                    blobs[field] = bytes.copyOfRange(i, i + len)
                    i += len
                }
                1 -> i += 8
                5 -> i += 4
                else -> throw IllegalStateException("wire type ${key and 7}")
            }
        }
    }

    fun has(field: Int) = field in varints || field in blobs
    fun int(field: Int): Int = (varints[field] ?: 0L).toInt()
    fun bool(field: Int): Boolean = (varints[field] ?: 0L) != 0L
    fun string(field: Int): String = blobs[field]?.toString(Charsets.UTF_8).orEmpty()
    fun message(field: Int): ProtoReader? = blobs[field]?.let(::ProtoReader)

    companion object {
        /** The next framed message from the stream (blocking). */
        fun readFrame(input: InputStream): ByteArray {
            var shift = 0
            var len = 0L
            while (true) {
                val b = input.read()
                if (b < 0) throw EOFException("connection closed")
                len = len or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 35) throw IllegalStateException("frame length too long")
            }
            if (len > 1_000_000) throw IllegalStateException("frame too big: $len")
            val buf = ByteArray(len.toInt())
            var off = 0
            while (off < buf.size) {
                val n = input.read(buf, off, buf.size - off)
                if (n < 0) throw EOFException("connection closed")
                off += n
            }
            return buf
        }
    }
}
