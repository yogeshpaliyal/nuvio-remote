package dev.skiff

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 *  - Activity (its own screen): what was sent to the TV, and how.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(host: String, tvName: String, onBack: () -> Unit, startOnActivity: Boolean = false, startOnUpdate: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tvLabel = tvName.ifEmpty { host.ifEmpty { stringResource(R.string.the_tv) } }
    val hostOk = host.isNotBlank()

    // Connection
    var paired by remember(host) { mutableStateOf(hostOk && Atv.isPaired(context, host)) }
    var pairOpen by remember { mutableStateOf(false) }
    var remoteVia by remember(host) { mutableStateOf(Connection.remote(context, host)) }
    var linkVia by remember(host) { mutableStateOf(Connection.link(context, host)) }
    var adb by remember(host) { mutableStateOf<TvLink.Adb?>(null) }     // null: checking
    var adbRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(host, adbRun) {
        adb = null
        if (!hostOk) return@LaunchedEffect
        adb = TvLink.check(context, host)
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
                output = r.allOutput.trimEnd().ifEmpty { context.getString(R.string.no_output, r.exitCode) } +
                    if (r.exitCode != 0 && r.allOutput.isNotBlank()) "\n" + context.getString(R.string.exit_code, r.exitCode) else ""
                ActivityLog.add(context, context.getString(R.string.log_command, c), "USB debugging", ok = r.exitCode == 0)
                Telemetry.event(context, "adb_command", "ok" to (r.exitCode == 0))
            } catch (e: Exception) {
                outputFailed = true
                output = context.getString(R.string.couldnt_reach, tvLabel, e.message ?: e.javaClass.simpleName)
                ActivityLog.add(context, context.getString(R.string.log_command, c), "USB debugging", ok = false)
                Telemetry.event(context, "adb_command", "ok" to false)
                Telemetry.error(context, "adb_command", e)
            } finally {
                running = false
                recent = rememberCommand(context, c)
            }
        }
    }

    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayName(context, uri) ?: context.getString(R.string.the_apk)
        installing = true
        installFailed = false
        installNote = context.getString(R.string.installing_on, name, tvLabel)
        scope.launch {
            val apk = File(context.cacheDir, "install.apk")
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { input -> apk.outputStream().use { input.copyTo(it) } }
                }
                TvLink.install(context, host, apk)
                installNote = context.getString(R.string.installed_on, name, tvLabel)
                ActivityLog.add(context, context.getString(R.string.log_installed, name), "USB debugging")
                Telemetry.event(context, "apk_install", "ok" to true)
            } catch (e: Exception) {
                installFailed = true
                installNote = context.getString(R.string.couldnt_install, name, e.message ?: e.javaClass.simpleName)
                ActivityLog.add(context, context.getString(R.string.log_install, name), "USB debugging", ok = false)
                Telemetry.event(context, "apk_install", "ok" to false)
                Telemetry.error(context, "apk_install", e)
            } finally {
                withContext(Dispatchers.IO) { apk.delete() }
                installing = false
            }
        }
    }

    var showActivity by remember { mutableStateOf(startOnActivity) }
    if (showActivity) {
        BackHandler { showActivity = false }
        ActivityScreen(onBack = { showActivity = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_to_skiff)) } },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // MARK: Activity (its own screen)
            val entries = ActivityLog.entries(context)
            Card(
                onClick = { showActivity = true },
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.activity), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            entries.firstOrNull()?.let { stringResource(R.string.activity_last, it.what, viaLabel(it.via)) }
                                ?: stringResource(R.string.activity_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }

            // MARK: Connection
            SectionHeader(if (hostOk) stringResource(R.string.connection_tv, tvLabel) else stringResource(R.string.connection))
            if (!hostOk) {
                Text(stringResource(R.string.pick_tv_first_main), style = MaterialTheme.typography.bodyLarge)
            } else {
                Panel {
                    StatusRow(stringResource(R.string.via_pairing), stringResource(if (paired) R.string.paired else R.string.not_paired), if (paired) true else null)
                    StatusRow(
                        stringResource(R.string.via_adb),
                        stringResource(
                            when (adb) {
                                null -> R.string.checking
                                TvLink.Adb.ALLOWED -> R.string.adb_allowed
                                TvLink.Adb.NOT_ALLOWED -> R.string.adb_not_allowed
                                TvLink.Adb.OFF -> R.string.adb_off
                            },
                        ),
                        adb?.let { it == TvLink.Adb.ALLOWED },
                        action = { if (adb != null) TextButton(onClick = { adbRun++ }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.check_again), Modifier.size(18.dp)) } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (paired) {
                            OutlinedButton(onClick = {
                                Atv.forget(context, host, tvName)
                                paired = false
                                ActivityLog.add(context, context.getString(R.string.log_forgot_pairing, tvLabel), "Pairing")
                                Telemetry.event(context, "forget_pairing")
                            }) { Text(stringResource(R.string.forget_pairing)) }
                        }
                        if (!pairOpen) Button(onClick = { pairOpen = true }) { Text(stringResource(if (paired) R.string.pair_again else R.string.pair)) }
                    }
                }
                if (pairOpen) PairCard(host, onPaired = { paired = true; pairOpen = false }, onCancel = { pairOpen = false })

                Panel {
                    Text(stringResource(R.string.remote), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ViaChoice(remoteVia) { remoteVia = it; Connection.setRemote(context, host, it) }
                    Text(stringResource(remoteHelp(remoteVia)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.links_and_titles), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ViaChoice(linkVia) { linkVia = it; Connection.setLink(context, host, it) }
                    Text(
                        stringResource(linkHelp(linkVia)) + " " + stringResource(R.string.link_help_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // MARK: ADB tools
            SectionHeader(stringResource(R.string.adb_tools))
            if (hostOk && adb != null && adb != TvLink.Adb.ALLOWED) {
                Text(
                    stringResource(R.string.adb_tools_need, tvLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val tools = hostOk && adb == TvLink.Adb.ALLOWED
            Panel {
                Text(stringResource(R.string.run_command), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.run_command_help, tvLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.command)) },
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
                    Button(onClick = { run(command) }, enabled = tools && !running && command.isNotBlank()) { Text(stringResource(R.string.run)) }
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
                Text(stringResource(R.string.install_apk), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.install_apk_help, tvLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
                        enabled = tools && !installing,
                    ) { Text(stringResource(R.string.choose_apk)) }
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

            // MARK: Privacy
            SectionHeader(stringResource(R.string.privacy))
            var telemetry by remember { mutableStateOf(Telemetry.enabled(context)) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.share_reports), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.share_reports_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = telemetry, onCheckedChange = { telemetry = it; Telemetry.setEnabled(context, it) })
                }
                // Debug builds: crash on purpose, to see a report reach Crashlytics (sent when the app opens again).
                if (Telemetry.debuggable(context)) {
                    OutlinedButton(onClick = { Telemetry.testCrash(context) }, enabled = telemetry) { Text(stringResource(R.string.test_crash)) }
                }
            }
            // MARK: About
            SectionHeader(stringResource(R.string.about))
            // Updates: the version, a check now, and a newer one highlighted (tap it for what's new).
            val update by Updates.update.collectAsState()
            var checking by remember { mutableStateOf(false) }
            var checkNote by remember { mutableStateOf<String?>(null) }
            var showUpdate by remember { mutableStateOf(startOnUpdate) }
            val current = Updates.current(context)
            Card(
                onClick = {
                    val u = update
                    if (u != null) showUpdate = true
                    else if (!checking) {
                        checking = true
                        checkNote = null
                        scope.launch {
                            checkNote = try {
                                if (Updates.check(context, force = true) == null) context.getString(R.string.latest_version) else null
                            } catch (e: Exception) {
                                Telemetry.error(context, "update_check", e)
                                context.getString(R.string.couldnt_check, e.message ?: e.javaClass.simpleName)
                            }
                            checking = false
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (update != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            update?.let { if (it.version.isNotBlank()) stringResource(R.string.update_to, it.version) else stringResource(R.string.update_available) }
                                ?: stringResource(R.string.check_for_updates),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when {
                                update != null -> stringResource(R.string.you_have_see_new, current)
                                checking -> stringResource(R.string.checking)
                                checkNote != null -> checkNote!!
                                else -> stringResource(R.string.version, current)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (checking) LoadingIndicator(Modifier.size(24.dp))
                    else if (update != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                }
            }
            update?.let { u -> if (showUpdate) UpdateSheet(u, onDismiss = { showUpdate = false }) }
            LinkRow(stringResource(R.string.report_issue), stringResource(R.string.report_issue_help)) {
                openLink(context, issueUrl(context))
            }
            LinkRow(stringResource(R.string.privacy_policy), stringResource(R.string.privacy_policy_help)) {
                openLink(context, "https://github.com/yogeshpaliyal/skiff/blob/main/PRIVACY.md")
            }

            // Debug builds: a made-up TV and history, for store screenshots.
            if (Telemetry.debuggable(context)) {
                SectionHeader(stringResource(R.string.demo_mode))
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.demo_tv), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.demo_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = Demo.on, onCheckedChange = { Demo.set(context, it) })
                    }
                }
            }
            // The version, quietly, at the very end.
            Text(
                stringResource(R.string.skiff_version, appVersion(context)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
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
            ) { Text(stringResource(v.label), maxLines = 1) }
        }
    }
}

private fun remoteHelp(via: Connection.Via) = when (via) {
    Connection.Via.AUTO -> R.string.remote_help_auto
    Connection.Via.PAIRING -> R.string.help_pairing_only
    Connection.Via.ADB -> R.string.help_adb_only
}

private fun linkHelp(via: Connection.Via) = when (via) {
    Connection.Via.AUTO -> R.string.link_help_auto
    else -> remoteHelp(via)
}

/** A row that opens something: title, a line about it, and a chevron. */
@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()})"
}.getOrDefault("?")

/**
 * A new GitHub issue, its body started: what happened, what was expected, and the details that help (Skiff's
 * version, Android version, phone model). Nothing about your TVs or history.
 */
private fun issueUrl(context: Context): String {
    val body = buildString {
        append("**What happened**\n\n\n**What you expected**\n\n\n**Steps to see it**\n1. \n\n---\n")
        append("Skiff ").append(appVersion(context))
        append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
        append(", ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
    }
    return "https://github.com/yogeshpaliyal/skiff/issues/new?body=" + Uri.encode(body)
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
