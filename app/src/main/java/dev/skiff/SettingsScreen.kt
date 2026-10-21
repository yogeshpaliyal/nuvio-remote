package dev.skiff

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.skiff.atv.Atv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings, for the TV picked:
 *  - Connection: paired or not, whether its ADB answers, and how the remote and links reach it (Automatic, Pairing,
 *    USB debugging); forget or redo the pairing;
 *  - ADB tools (USB debugging): a shell command, an APK from the phone installed on the TV;
 *  - Activity: what was sent to the TV, and how.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(host: String, tvName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tvLabel = tvName.ifEmpty { host.ifEmpty { "the TV" } }
    val hostOk = host.isNotBlank()

    // Connection
    var paired by remember(host) { mutableStateOf(hostOk && Atv.isPaired(context, host)) }
    var pairOpen by remember { mutableStateOf(false) }
    var remoteVia by remember(host) { mutableStateOf(Connection.remote(context, host)) }
    var linkVia by remember(host) { mutableStateOf(Connection.link(context, host)) }
    var adbOk by remember(host) { mutableStateOf<Boolean?>(null) }     // null: checking
    var adbRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(host, adbRun) {
        adbOk = null
        if (!hostOk) return@LaunchedEffect
        adbOk = runCatching { TvLink.shell(context, host, "true") }.isSuccess
    }

    // ADB tools
    var command by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(recentCommands(context)) }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<String?>(null) }
    var outputFailed by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var installNote by remember { mutableStateOf<String?>(null) }
    var installFailed by remember { mutableStateOf(false) }

    fun run(cmd: String) {
        val c = cmd.trim()
        if (c.isEmpty() || running) return
        running = true
        output = null
        scope.launch {
            try {
                val r = TvLink.shell(context, host, c)
                outputFailed = r.exitCode != 0
                output = r.allOutput.trimEnd().ifEmpty { "(no output, exit ${r.exitCode})" } +
                    if (r.exitCode != 0 && r.allOutput.isNotBlank()) "\n(exit ${r.exitCode})" else ""
                ActivityLog.add(context, "Command: $c", "USB debugging", ok = r.exitCode == 0)
            } catch (e: Exception) {
                outputFailed = true
                output = "Couldn't reach $tvLabel: ${e.message ?: e.javaClass.simpleName}"
                ActivityLog.add(context, "Command: $c", "USB debugging", ok = false)
            } finally {
                running = false
                recent = rememberCommand(context, c)
            }
        }
    }

    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayName(context, uri) ?: "the APK"
        installing = true
        installFailed = false
        installNote = "Installing $name on $tvLabel…"
        scope.launch {
            val apk = File(context.cacheDir, "install.apk")
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { input -> apk.outputStream().use { input.copyTo(it) } }
                }
                TvLink.install(context, host, apk)
                installNote = "Installed $name on $tvLabel."
                ActivityLog.add(context, "Installed $name", "USB debugging")
            } catch (e: Exception) {
                installFailed = true
                installNote = "Couldn't install $name: ${e.message ?: e.javaClass.simpleName}"
                ActivityLog.add(context, "Install $name", "USB debugging", ok = false)
            } finally {
                withContext(Dispatchers.IO) { apk.delete() }
                installing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Skiff") } },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // MARK: Connection
            SectionHeader(if (hostOk) "Connection · $tvLabel" else "Connection")
            if (!hostOk) {
                Text("Pick a TV first, on Skiff's main screen.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Panel {
                    StatusRow("Pairing", if (paired) "Paired" else "Not paired", if (paired) true else null)
                    StatusRow(
                        "USB debugging",
                        when (adbOk) { null -> "Checking…"; true -> "On, this phone allowed"; false -> "Off, or this phone not allowed yet" },
                        adbOk,
                        action = { if (adbOk != null) TextButton(onClick = { adbRun++ }) { Icon(Icons.Rounded.Refresh, "Check again", Modifier.size(18.dp)) } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (paired) {
                            OutlinedButton(onClick = {
                                Atv.forget(context, host)
                                paired = false
                                ActivityLog.add(context, "Forgot the pairing with $tvLabel", "Pairing")
                            }) { Text("Forget pairing") }
                        }
                        if (!pairOpen) Button(onClick = { pairOpen = true }) { Text(if (paired) "Pair again" else "Pair") }
                    }
                }
                if (pairOpen) PairCard(host, onPaired = { paired = true; pairOpen = false }, onCancel = { pairOpen = false })

                Panel {
                    Text("Remote", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ViaChoice(remoteVia) { remoteVia = it; Connection.setRemote(context, host, it) }
                    Text(viaHelp(remoteVia, "keys and text"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                    Text("Links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ViaChoice(linkVia) { linkVia = it; Connection.setLink(context, host, it) }
                    Text(
                        viaHelp(linkVia, "links") + " Over pairing the TV picks the app; over USB debugging you pick it, and the link's rules apply. Nuvio titles always use USB debugging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // MARK: ADB tools
            SectionHeader("ADB tools")
            if (hostOk && adbOk == false) {
                Text(
                    "These need USB debugging on $tvLabel (Settings → System → Developer options), and this phone allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val tools = hostOk && adbOk == true
            Panel {
                Text("Run a command", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "As adb shell runs it, on $tvLabel. It can change or remove anything on the TV: run only what you know.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command") },
                    placeholder = { Text("pm list packages -3") },
                    singleLine = true,
                    enabled = tools,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { run(command) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recent.isNotEmpty()) {
                    // Recent commands fill the box: sent only with Run.
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in recent) {
                            SuggestionChip(onClick = { command = c }, label = { Text(c, fontFamily = FontFamily.Monospace, maxLines = 1) }, enabled = tools)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { run(command) }, enabled = tools && !running && command.isNotBlank()) { Text("Run") }
                    if (running) LoadingIndicator(Modifier.size(28.dp))
                }
                output?.let { out ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (outputFailed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SelectionContainer {
                            Text(
                                out,
                                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
            Panel {
                Text("Install an APK", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pick an .apk on this phone: it's installed on $tvLabel (an update keeps the app's data).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
                        enabled = tools && !installing,
                    ) { Text("Choose an APK") }
                    if (installing) LoadingIndicator(Modifier.size(28.dp))
                }
                installNote?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (installFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // MARK: Activity
            val entries = ActivityLog.entries(context)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader("Activity") }
                if (entries.isNotEmpty()) TextButton(onClick = { ActivityLog.clear(context) }) { Text("Clear") }
            }
            if (entries.isEmpty()) {
                Text(
                    "What Skiff sends to the TV shows here, with how it went: Pairing or USB debugging.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Panel {
                    for (e in entries) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (e.ok) Color(0xFF3DDC84) else MaterialTheme.colorScheme.error),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.what, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(
                                    e.via + (if (e.ok) "" else " · failed") + " · " + DateUtils.formatDateTime(
                                        context, e.time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** A line of the connection's state: `good` true (green), false (red), null (neutral). */
@Composable
private fun StatusRow(label: String, value: String, good: Boolean?, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(
                when (good) { true -> Color(0xFF3DDC84); false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.outline },
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

@Composable
private fun ViaChoice(via: Connection.Via, onChange: (Connection.Via) -> Unit) {
    val all = Connection.Via.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        all.forEachIndexed { i, v ->
            SegmentedButton(
                selected = via == v,
                onClick = { onChange(v) },
                shape = SegmentedButtonDefaults.itemShape(i, all.size),
            ) { Text(v.label, maxLines = 1) }
        }
    }
}

private fun viaHelp(via: Connection.Via, what: String) = when (via) {
    Connection.Via.AUTO -> "Automatic: $what over pairing when paired, else over USB debugging."
    Connection.Via.PAIRING -> "Only over pairing (the Google TV remote protocol)."
    Connection.Via.ADB -> "Only over USB debugging (ADB)."
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

private fun recentCommands(context: Context): List<String> =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("recentCommands", "")!!
        .split('\n').filter { it.isNotBlank() }

/** The last 8 commands run, newest first. */
private fun rememberCommand(context: Context, command: String): List<String> {
    val list = (listOf(command) + recentCommands(context).filter { it != command }).take(8)
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("recentCommands", list.joinToString("\n")).apply()
    return list
}
