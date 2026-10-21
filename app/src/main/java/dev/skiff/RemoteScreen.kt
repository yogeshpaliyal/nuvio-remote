package dev.skiff

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.skiff.atv.Atv
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A remote for the TV, laid out like a physical one, by importance: the D-pad and OK first and biggest; then Home
 * (bigger) between Back and Menu; then two capsules, a volume rocker and playback (play/pause the biggest). Arrows,
 * volume and seek repeat when held. Power sits apart, in red. One connection while it's open (TvLink.Remote).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoteScreen(host: String, tvName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var remote by remember { mutableStateOf<TvKeys?>(null) }
    var via by remember { mutableStateOf("") }        // "paired" (Google TV remote protocol) or "USB debugging"
    // Pairing (no developer options): the TV shows a code, typed here.
    var pairing by remember { mutableStateOf<Atv.Pairing?>(null) }
    var pairBusy by remember { mutableStateOf(false) }
    var pairNote by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val ready = remote != null

    // Connected while the remote is showing: paired (the Google TV remote protocol) first, else the TV's ADB (USB
    // debugging; the first time, the TV asks to allow this phone), else: pair.
    LaunchedEffect(host, attempt) {
        error = null
        remote?.close()
        remote = null
        if (host.isBlank()) {
            error = "Pick a TV first."
            return@LaunchedEffect
        }
        var pairedFailed: String? = null
        if (Atv.isPaired(context, host)) {
            try {
                remote = Atv.connect(context, host)
                via = "paired"
                return@LaunchedEffect
            } catch (e: Exception) {
                pairedFailed = e.message
            }
        }
        try {
            remote = TvLink.remote(context, host)
            via = "USB debugging"
        } catch (e: Exception) {
            error = pairedFailed ?: "Pair with ${tvName.ifEmpty { "the TV" }} to use the remote: no USB debugging needed."
        }
    }
    DisposableEffect(Unit) { onDispose { remote?.close(); pairing?.close() } }

    fun startPairing() {
        pairBusy = true
        pairNote = null
        scope.launch {
            try {
                pairing?.close()
                pairing = Atv.pair(context, host)
                code = ""
            } catch (e: Exception) {
                pairNote = "Couldn't start pairing: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                pairBusy = false
            }
        }
    }

    fun finishPairing() {
        val p = pairing ?: return
        pairBusy = true
        scope.launch {
            try {
                when (p.finish(code)) {
                    Atv.Pairing.Result.PAIRED -> {
                        pairing = null
                        pairNote = null
                        attempt++       // connect, paired
                    }
                    Atv.Pairing.Result.MISTYPED -> pairNote = "That code doesn't match: check it on the TV."
                    Atv.Pairing.Result.REFUSED -> {
                        pairing = null
                        pairNote = "The TV didn't accept it. Pair again."
                    }
                }
            } finally {
                pairBusy = false
            }
        }
    }

    /** A key now. A dropped connection reconnects (that key is lost: press again). */
    fun press(code: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val r = remote ?: return
        scope.launch {
            try {
                r.key(code)
            } catch (e: Exception) {
                remote?.close()
                remote = null
                attempt++
            }
        }
    }

    fun type() {
        val t = typed
        val r = remote ?: return
        if (t.isEmpty()) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        scope.launch { runCatching { r.text(t) }.onSuccess { typed = "" } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tvName.ifEmpty { "Remote" }, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(
                                    when {
                                        error != null -> MaterialTheme.colorScheme.error
                                        ready -> Color(0xFF3DDC84)
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when { error != null -> "Not connected"; ready -> "Connected · $via"; else -> "Connecting…" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Skiff") } },
                actions = {
                    // Sleep / wake (KEYCODE_POWER toggles it on Android TV): apart, and red.
                    IconButton(
                        onClick = { press(KeyEvent.KEYCODE_POWER) },
                        enabled = ready,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Icon(AppIcons.Power, "Power") }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            error?.let { msg ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { attempt++ }) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            if (!ready && error == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Pairing: when there's no connection, or to stop needing USB debugging.
            if (error != null || via == "USB debugging" || pairing != null) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pair with this TV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (pairing == null) "Like the Google TV app: the TV shows a code, you type it here. No developer options or USB debugging."
                            else "Type the code shown on the TV.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (pairing != null) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
                                label = { Text("Code on the TV") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (code.length == 6) finishPairing() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        pairNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (pairing == null) {
                                Button(onClick = { startPairing() }, enabled = !pairBusy && host.isNotBlank()) { Text("Show a code on the TV") }
                            } else {
                                Button(onClick = { finishPairing() }, enabled = !pairBusy && code.length == 6) { Text("Pair") }
                                TextButton(onClick = { pairing?.close(); pairing = null }) { Text("Cancel") }
                            }
                            if (pairBusy) LoadingIndicator(Modifier.size(28.dp))
                        }
                    }
                }
            }

            // 1. The D-pad: the hero.
            DPad(ready) { press(it) }

            // 2. Navigation: Home biggest, Back and Menu either side.
            Row(
                Modifier.fillMaxWidth().widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                LabeledKey("Back", ready) { RoundKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", 60.dp, ready, Tone.Secondary) { press(KeyEvent.KEYCODE_BACK) } }
                LabeledKey("Home", ready) { RoundKey(Icons.Rounded.Home, "Home", 76.dp, ready, Tone.Primary) { press(KeyEvent.KEYCODE_HOME) } }
                LabeledKey("Menu", ready) { RoundKey(Icons.Rounded.Menu, "Menu", 60.dp, ready, Tone.Secondary) { press(KeyEvent.KEYCODE_MENU) } }
            }

            // 3. Volume rocker and playback, as two capsules.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Capsule("Volume", Modifier.weight(1f)) {
                    CapsuleKey(AppIcons.VolumeDown, "Volume down", ready, repeat = true) { press(KeyEvent.KEYCODE_VOLUME_DOWN) }
                    CapsuleKey(AppIcons.VolumeOff, "Mute", ready, small = true) { press(KeyEvent.KEYCODE_VOLUME_MUTE) }
                    CapsuleKey(AppIcons.VolumeUp, "Volume up", ready, repeat = true) { press(KeyEvent.KEYCODE_VOLUME_UP) }
                }
                Capsule("Playback", Modifier.weight(1f)) {
                    CapsuleKey(AppIcons.FastRewind, "Rewind", ready, repeat = true, small = true) { press(KeyEvent.KEYCODE_MEDIA_REWIND) }
                    RoundKey(AppIcons.PlayPause, "Play / pause", 52.dp, ready, Tone.Tertiary) { press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                    CapsuleKey(AppIcons.FastForward, "Forward", ready, repeat = true, small = true) { press(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }
                }
            }

            // 4. Typing into the TV, only when wanted.
            AnimatedVisibility(!typing) {
                FilledTonalButton(onClick = { typing = true }, enabled = ready) {
                    Icon(AppIcons.Keyboard, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Type on the TV")
                }
            }
            AnimatedVisibility(typing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type on the TV") },
                    placeholder = { Text("Select a search box on the TV first") },
                    leadingIcon = { Icon(AppIcons.Keyboard, null) },
                    trailingIcon = { TextButton(onClick = { type() }, enabled = ready && typed.isNotEmpty()) { Text("Send") } },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { type() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private enum class Tone { Primary, Secondary, Tertiary }

/** The D-pad: one big rounded pad, chevrons at its edges, OK in the middle. Arrows repeat when held. */
@Composable
private fun DPad(enabled: Boolean, onKey: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(56.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp).aspectRatio(1f),
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            val arrow = Modifier.size(width = 120.dp, height = 84.dp)
            val side = Modifier.size(width = 84.dp, height = 120.dp)
            PadKey(Icons.Rounded.KeyboardArrowUp, "Up", enabled, arrow.align(Alignment.TopCenter)) { onKey(KeyEvent.KEYCODE_DPAD_UP) }
            PadKey(Icons.Rounded.KeyboardArrowDown, "Down", enabled, arrow.align(Alignment.BottomCenter)) { onKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            PadKey(Icons.Rounded.KeyboardArrowLeft, "Left", enabled, side.align(Alignment.CenterStart)) { onKey(KeyEvent.KEYCODE_DPAD_LEFT) }
            PadKey(Icons.Rounded.KeyboardArrowRight, "Right", enabled, side.align(Alignment.CenterEnd)) { onKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
            Pressable(
                enabled = enabled, repeat = false, shape = CircleShape,
                rest = MaterialTheme.colorScheme.primary, pressed = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.align(Alignment.Center).size(128.dp),
                onPress = { onKey(KeyEvent.KEYCODE_DPAD_CENTER) },
            ) { pressedNow ->
                Text(
                    "OK", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = if (pressedNow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun PadKey(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onPress: () -> Unit) {
    Pressable(
        enabled = enabled, repeat = true, shape = RoundedCornerShape(36.dp),
        rest = Color.Transparent, pressed = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier, onPress = onPress,
    ) {
        Icon(icon, label, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LabeledKey(label: String, enabled: Boolean, key: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        key()
        Text(
            label, style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, size: Dp, enabled: Boolean, tone: Tone, onPress: () -> Unit) {
    val (rest, content) = when (tone) {
        Tone.Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Tone.Secondary -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Tone.Tertiary -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
    }
    Pressable(
        enabled = enabled, repeat = false, shape = CircleShape,
        rest = rest, pressed = MaterialTheme.colorScheme.inversePrimary,
        modifier = Modifier.size(size), onPress = onPress,
    ) {
        Icon(icon, label, Modifier.size(size * 0.42f), tint = content)
    }
}

/** A labeled capsule holding a row of keys (the volume rocker, playback). */
@Composable
private fun Capsule(label: String, modifier: Modifier, keys: @Composable RowScope.() -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(68.dp)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = keys,
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CapsuleKey(icon: ImageVector, label: String, enabled: Boolean, repeat: Boolean = false, small: Boolean = false, onPress: () -> Unit) {
    Pressable(
        enabled = enabled, repeat = repeat, shape = CircleShape,
        rest = Color.Transparent, pressed = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(52.dp), onPress = onPress,
    ) {
        Icon(icon, label, Modifier.size(if (small) 22.dp else 28.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A key that acts on touch down (a remote shouldn't wait for the finger to lift), shrinks and changes color while
 * pressed, and with `repeat` keeps pressing while held: after 400 ms, then every 120 ms.
 */
@Composable
private fun Pressable(
    enabled: Boolean, repeat: Boolean, shape: Shape, rest: Color, pressed: Color, modifier: Modifier,
    onPress: () -> Unit, content: @Composable (pressed: Boolean) -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) 0.9f else 1f, label = "press")
    val color by animateColorAsState(
        when { !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (rest == Color.Transparent) 0f else 1f); down -> pressed; else -> rest },
        label = "press",
    )
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(color)
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(repeat) {
                    detectTapGestures(onPress = {
                        down = true
                        onPress()
                        if (repeat) {
                            coroutineScope {
                                val job = launch {
                                    delay(400)
                                    while (true) {
                                        onPress()
                                        delay(120)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        } else {
                            tryAwaitRelease()
                        }
                        down = false
                    })
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(down)
    }
}

