package dev.skiff.atv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import dev.skiff.Demo
import dev.skiff.TvKeys
import android.security.keystore.KeyGenParameterSpec
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Calendar
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The Android TV Remote protocol (v2): what the Google TV phone app speaks, served by every Google TV's "Android TV
 * Remote Service". No developer options: the phone pairs once with a code the TV shows, then controls it over TLS.
 *  - pairing, port 6467: [pair];
 *  - the remote, port 6466: [connect] — keys, text, and app links (a link opened on the TV, in the app for it).
 *
 * The phone is known to the TV by a client certificate (an RSA key in the Android Keystore, never leaving the
 * phone). The TV's certificate is self-signed: it's pinned at pairing (its SHA-256) and checked on every connection.
 */
object Atv {
    private const val PAIRING_PORT = 6467
    private const val REMOTE_PORT = 6466
    private const val ALIAS = "skiff-atv-client"
    private const val PREFS = "atv"

    /** The TV refused the phone: not paired (or the TV forgot it). */
    class NotPaired(message: String) : Exception(message)

    // MARK: Pairing

    /** A pairing in progress: the TV shows a 6-character code; [finish] with it. */
    class Pairing internal constructor(private val context: Context, private val host: String, private val socket: SSLSocket) : AutoCloseable {
        enum class Result { PAIRED, MISTYPED, REFUSED }

        /**
         * Off the main thread. MISTYPED: the code doesn't match these certificates (checked here, nothing sent: type
         * it again). REFUSED: the TV said no, and this pairing is over (start again).
         */
        suspend fun finish(code: String): Result = withContext(Dispatchers.IO) {
            val server = socket.session.peerCertificates[0] as X509Certificate
            val secret = Messages.pairingSecret(clientCertificate(context).publicKey as RSAPublicKey, server.publicKey as RSAPublicKey, code)
                ?: return@withContext Result.MISTYPED
            val reply = runCatching {
                ProtoWriter.frame(socket.outputStream, Messages.secret(secret))
                Messages.Outer(ProtoReader.readFrame(socket.inputStream))
            }.getOrNull()
            close()
            if (reply == null || reply.status != 200 || !reply.isSecretAck) return@withContext Result.REFUSED
            prefs(context).edit().putString(key(host), fingerprint(server)).apply()
            Result.PAIRED
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    /**
     * Off the main thread. Starts pairing: the TV shows a code (and "Skiff" as the device). Returns once it does.
     * Throws when the TV can't be reached or refuses.
     */
    suspend fun pair(context: Context, host: String): Pairing = withContext(Dispatchers.IO) {
        val socket = open(context, host, PAIRING_PORT, pinned = null)
        try {
            ProtoWriter.frame(socket.outputStream, Messages.pairingRequest("Skiff on ${Build.MODEL}"))
            while (true) {
                val m = Messages.Outer(ProtoReader.readFrame(socket.inputStream))
                if (m.status != 200) error("The TV refused pairing (status ${m.status}).")
                when {
                    m.isPairingRequestAck -> ProtoWriter.frame(socket.outputStream, Messages.options())
                    m.isOptions -> ProtoWriter.frame(socket.outputStream, Messages.configuration())
                    m.isConfigurationAck -> return@withContext Pairing(context, host, socket)   // the code is on screen
                    else -> error("Unexpected message while pairing.")
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    /** This phone paired with that TV (at that address) before. */
    fun isPaired(context: Context, host: String) = if (Demo.on) host.trim() == Demo.HOST else prefs(context).contains(key(host))

    /** Forget a TV, at this address and by its name (the TV keeps its side until its remote service's data is cleared). */
    fun forget(context: Context, host: String, name: String = "") {
        val p = prefs(context)
        val fp = p.getString(key(host), null)
        p.edit().apply {
            remove(key(host))
            if (name.isNotBlank()) remove(nameKey(name))
            // Its other addresses too: the same TV (the same certificate).
            if (fp != null) for ((k, v) in p.all) if (v == fp) remove(k)
        }.apply()
    }

    /**
     * A TV's pairing is kept by its address; this also keeps it by the TV's name (as it announces itself), so that
     * when the router gives the TV a new address, [adopt] finds the pairing again. Call it when a paired TV is picked.
     */
    fun rememberName(context: Context, host: String, name: String) {
        if (name.isBlank() || Demo.on) return
        val fp = prefs(context).getString(key(host), null) ?: return
        if (prefs(context).getString(nameKey(name), null) != fp) prefs(context).edit().putString(nameKey(name), fp).apply()
    }

    /**
     * A TV found at `host` under `name`: when this phone paired with a TV of that name at another address, the
     * pairing carries over to this one (its certificate is checked on connecting: another TV with the same name
     * isn't trusted, it asks to pair). True when it did.
     */
    fun adopt(context: Context, host: String, name: String): Boolean {
        if (name.isBlank() || Demo.on || prefs(context).contains(key(host))) return false
        val fp = prefs(context).getString(nameKey(name), null) ?: return false
        prefs(context).edit().putString(key(host), fp).apply()
        return true
    }

    // MARK: The remote

    /** A connected remote: keys, text, links; the TV's pings are answered in the background. Close it when done. */
    class Remote internal constructor(private val socket: SSLSocket) : TvKeys {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val writes = Mutex()
        private val started = CompletableDeferred<Unit>()
        @Volatile private var imeCounter = 0
        @Volatile private var fieldCounter = 0
        /** The TV sent its input method's counters for a focused field: text can go as an edit. */
        @Volatile private var imeReady = false
        // The counters of the focused field as the TV's key inject (20) gives them: app_info.counter, counter_field.
        @Volatile private var focusIme: Int? = null
        @Volatile private var focusField: Int? = null
        private val fieldText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        override val field: kotlinx.coroutines.flow.StateFlow<String?> = fieldText
        @Volatile private var features = Messages.FEATURES
        @Volatile var closed = false
            private set
        override val isOpen: Boolean get() = !closed

        internal fun start() {
            scope.launch {
                try {
                    while (true) {
                        val bytes = ProtoReader.readFrame(socket.inputStream)
                        val m = Messages.Remote(bytes)
                        if (m.pingVal1 == null) {
                            Log.d("Skiff", "TV sent RemoteMessage fields ${m.fields}: ${bytes.joinToString("") { "%02x".format(it) }.take(400)}")
                        }
                        when {
                            m.configure != null -> {
                                features = Messages.FEATURES and m.configure.int(1)
                                send(Messages.remoteConfigure(features))
                            }
                            m.setActive -> send(Messages.remoteSetActive(features))
                            m.pingVal1 != null -> send(Messages.pingResponse(m.pingVal1))
                            m.imeBatchEdit != null -> {
                                imeCounter = m.imeBatchEdit.int(1)
                                fieldCounter = m.imeBatchEdit.int(2)
                                imeReady = true
                                fieldText.value = m.imeFieldText ?: fieldText.value ?: ""
                            }
                            m.imeFieldCounter != null -> {
                                focusField = m.imeFieldCounter
                                m.imeAppCounter?.let { focusIme = it }
                                imeReady = true
                                fieldText.value = m.imeFieldText ?: ""
                            }
                            m.start != null -> started.complete(Unit)
                        }
                    }
                } catch (e: Exception) {
                    started.completeExceptionally(e)
                    close()
                }
            }
        }

        internal suspend fun awaitStarted() = withTimeout(8_000) { started.await() }

        /** A key (KeyEvent.KEYCODE_*: the protocol uses Android's codes). */
        override suspend fun key(code: Int) = send(Messages.keyInject(code))

        /**
         * Text into the TV's focused field. When the TV has sent its input method's counters for the field (it does
         * when a search box gets focus), as an edit of that field, the way the Google TV app types; the TV ignores
         * letter keys there. Otherwise as key presses (Shift held for capitals and symbols).
         */
        override suspend fun text(s: String) {
            if (s.isEmpty() && !imeReady) return
            val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(s.toCharArray())
            if (imeReady || events == null) {
                edit(s)
                return
            }
            Log.d("Skiff", "Text as key presses (no IME counters from the TV yet)")
            for (e in events) {
                val modifier = KeyEvent.isModifierKey(e.keyCode)
                when {
                    modifier -> send(Messages.keyInject(
                        e.keyCode, if (e.action == KeyEvent.ACTION_DOWN) Messages.DIRECTION_DOWN else Messages.DIRECTION_UP,
                    ))
                    e.action == KeyEvent.ACTION_DOWN -> send(Messages.keyInject(e.keyCode))
                }
            }
        }

        /** The pairs of counters to send an edit with: the focused field's (20/22) first, then the batch edit's (21). */
        private fun counterPairs() = listOfNotNull(
            focusIme?.let { ime -> focusField?.let { ime to it } },
            imeCounter to fieldCounter,
        ).distinct()

        private suspend fun sendEdit(text: String, insert: Int = 1) {
            for ((ime, field) in counterPairs()) send(Messages.imeText(ime, field, text, insert))
        }

        /** Waits (a little) for the TV to say its field reads `text`: it answers each edit it takes. */
        private suspend fun tvShows(text: String) =
            withTimeoutOrNull(700) { fieldText.first { it == text } } != null

        /**
         * Makes the TV's field read `target`. The TV takes an edit when the text grows (what's typed is added), but
         * not when it shrinks: then the same edit with insert off is tried, and if the field still reads the old
         * text, Delete is pressed for what has to go, and the rest is added. Each step checks the TV's answer, so
         * nothing is deleted twice.
         */
        private suspend fun edit(target: String) {
            val now = fieldText.value ?: ""
            if (target == now) return
            if (target.startsWith(now)) {
                Log.d("Skiff", "Edit: add (counters ${counterPairs()})")
                sendEdit(target)
                return
            }
            Log.d("Skiff", "Edit: shrink ${now.length} → ${target.length}, insert off first")
            sendEdit(target, insert = 0)
            if (tvShows(target)) return
            val keep = now.commonPrefixWith(target).length
            val current = fieldText.value ?: now
            val deletes = current.length - keep
            Log.d("Skiff", "Edit: not taken; Delete ×$deletes, then add ${target.length - keep}")
            repeat(deletes) { send(Messages.keyInject(KeyEvent.KEYCODE_DEL)) }
            if (target.length > keep) {
                tvShows(target.substring(0, keep))
                sendEdit(target)
            }
        }

        /** Opens a link on the TV (an app's own link, a web link…), in the app the TV picks for it. */
        suspend fun openLink(link: String) = send(Messages.appLink(link))

        private suspend fun send(message: ByteArray) = writes.withLock {
            withContext(Dispatchers.IO) { ProtoWriter.frame(socket.outputStream, message) }
        }

        override fun close() {
            closed = true
            runCatching { socket.close() }
            scope.cancel()
        }
    }

    /**
     * Off the main thread. A connected, started remote. Throws [NotPaired] when the TV refuses the phone (pair
     * first), or the TV's certificate isn't the one paired with.
     */
    suspend fun connect(context: Context, host: String): Remote = withContext(Dispatchers.IO) {
        val pinned = prefs(context).getString(key(host), null) ?: throw NotPaired("Not paired with this TV yet.")
        val socket = try {
            open(context, host, REMOTE_PORT, pinned)
        } catch (e: javax.net.ssl.SSLException) {
            throw NotPaired("The TV didn't accept this phone: pair again. (${e.message})")
        }
        val remote = Remote(socket)
        remote.start()
        try {
            remote.awaitStarted()
        } catch (e: Exception) {
            remote.close()
            throw NotPaired("The TV didn't start the remote: pair again. (${e.message ?: e.javaClass.simpleName})")
        }
        remote
    }

    /** Off the main thread. Connects, opens a link on the TV (the TV picks the app), and disconnects. */
    suspend fun openLink(context: Context, host: String, link: String) {
        if (Demo.on) return kotlinx.coroutines.delay(600)
        val remote = connect(context, host)
        try {
            remote.openLink(link)
            kotlinx.coroutines.delay(300)     // let the TV read it before the connection closes
        } finally {
            remote.close()
        }
    }

    // MARK: TLS and the certificate

    /** TLS to the TV with our client certificate; `pinned`: the TV's certificate must have this SHA-256. */
    private fun open(context: Context, host: String, port: Int, pinned: String?): SSLSocket {
        clientCertificate(context)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, null) }
        val tls = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(pinning(pinned)), null) }
        val socket = tls.socketFactory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(host.trim(), port), 5_000)
        socket.soTimeout = 0
        socket.startHandshake()
        return socket
    }

    /**
     * The TV's certificate is self-signed (no authority to check it against): any is accepted to pair with, and
     * after that only the one paired with (trust on first use, like SSH).
     */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun pinning(pinned: String?) = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
            if (pinned != null && fingerprint(chain[0]) != pinned) {
                throw java.security.cert.CertificateException("This isn't the TV paired with (its certificate changed): pair again.")
            }
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** Our certificate, made once: RSA 2048 in the Android Keystore, self-signed. */
    private fun clientCertificate(context: Context): X509Certificate {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getCertificate(ALIAS) as? X509Certificate)?.let { return it }
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setCertificateSubject(X500Principal("CN=Skiff, O=Skiff"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(start.time)
            .setCertificateNotAfter(end.time)
            // TLS: signatures for the handshake (TLS 1.2 PKCS#1, TLS 1.3 PSS).
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        return ks.apply { load(null) }.getCertificate(ALIAS) as X509Certificate
    }

    private fun fingerprint(cert: X509Certificate) =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(host: String) = "paired:" + host.trim()
    private fun nameKey(name: String) = "pairedName:" + name.trim().lowercase()
}
