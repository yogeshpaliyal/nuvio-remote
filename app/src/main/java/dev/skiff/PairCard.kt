package dev.skiff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.skiff.atv.Atv
import kotlinx.coroutines.launch

/**
 * Pairing with a TV (the Google TV remote protocol: no developer options): the TV shows a code, typed here. In the
 * remote (when there's no connection, or to stop needing USB debugging) and in Settings.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PairCard(
    host: String,
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    title: String = "Pair with this TV",
    intro: String = "Like the Google TV app: the TV shows a code, you type it here. No developer options or USB debugging.",
    footer: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pairing by remember { mutableStateOf<Atv.Pairing?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { pairing?.close() } }

    fun start() {
        busy = true
        note = null
        scope.launch {
            try {
                pairing?.close()
                pairing = Atv.pair(context, host)
                code = ""
            } catch (e: Exception) {
                note = "Couldn't start pairing: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun finish() {
        val p = pairing ?: return
        busy = true
        scope.launch {
            try {
                when (p.finish(code)) {
                    Atv.Pairing.Result.PAIRED -> {
                        pairing = null
                        note = null
                        ActivityLog.add(context, "Paired with ${host.trim()}", "Pairing")
                        Telemetry.event(context, "pair")
                        onPaired()
                    }
                    Atv.Pairing.Result.MISTYPED -> note = "That code doesn't match: check it on the TV."
                    Atv.Pairing.Result.REFUSED -> {
                        pairing = null
                        note = "The TV didn't accept it. Pair again."
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (pairing == null) intro else "Type the code shown on the TV.",
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
                    keyboardActions = KeyboardActions(onDone = { if (code.length == 6) finish() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pairing == null) {
                    Button(onClick = { start() }, enabled = !busy && host.isNotBlank()) { Text("Show a code on the TV") }
                    onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
                } else {
                    Button(onClick = { finish() }, enabled = !busy && code.length == 6) { Text("Pair") }
                    TextButton(onClick = { pairing?.close(); pairing = null; onCancel?.invoke() }) { Text("Cancel") }
                }
                if (busy) LoadingIndicator(Modifier.size(28.dp))
            }
            if (pairing == null) footer()
        }
    }
}
