package dev.nuvioremote

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** The last IMDb page shared to us, read by the screen (null = nothing new). */
    private var shared by mutableStateOf<Title.Shared?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shared = sharedTitle(intent)
        setContent {
            AppTheme {
                RemoteScreen(shared, onSharedUsed = { shared = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedTitle(intent)?.let { shared = it }
    }

    /** Chrome shares the page's URL as the text and its title as the subject; the IMDb app, both in the text. */
    private fun sharedTitle(intent: Intent?): Title.Shared? =
        if (intent?.action == Intent.ACTION_SEND) {
            Title.fromShared(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
        } else null
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private const val PREFS = "remote"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteScreen(shared: Title.Shared?, onSharedUsed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf(prefs.getString("host", "").orEmpty()) }
    // The TV picked from the list, by name: if the router gives it a new address, we follow it.
    var tvName by remember { mutableStateOf(prefs.getString("tvName", "").orEmpty()) }
    var tvs by remember { mutableStateOf(emptyList<TvFinder.Tv>()) }
    var searching by remember { mutableStateOf(true) }
    var searchRun by remember { mutableIntStateOf(0) }
    val finder = remember {
        TvFinder(context) { found ->
            tvs = found
            val same = found.firstOrNull { it.ready && it.name == tvName }
            when {
                same != null -> host = same.host
                host.isBlank() -> found.singleOrNull { it.ready }?.let { host = it.host; tvName = it.name }
            }
        }
    }
    DisposableEffect(Unit) {
        finder.start()
        onDispose { finder.stop() }
    }
    LaunchedEffect(searchRun) {
        searching = true
        delay(8_000)
        searching = false
    }
    var series by remember { mutableStateOf(false) }
    var imdbId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var season by remember { mutableStateOf("1") }
    var episode by remember { mutableStateOf("1") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    // The title was filled in for you (from the share or the lookup): a later lookup may replace it; yours, not.
    var autoName by remember { mutableStateOf(true) }
    var lookingUp by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    // Shared a page: open it on the TV as soon as it's known to be a movie (a series waits for its episode).
    var openWhenKnown by remember { mutableStateOf(false) }
    var shareRun by remember { mutableIntStateOf(0) }     // the same page shared again looks it up again

    val hostOk = host.trim().isNotEmpty()
    val idOk = Title.isImdbId(imdbId)
    val seasonN = season.toIntOrNull()
    val episodeN = episode.toIntOrNull()
    val episodeOk = !series || (seasonN != null && seasonN > 0 && episodeN != null && episodeN > 0)
    val ready = hostOk && idOk && episodeOk && !busy

    fun openOnTv() {
        busy = true
        failed = false
        status = "Connecting… The first time, accept “Allow USB debugging?” on the TV (tick Always allow)."
        scope.launch {
            try {
                var n = name.trim()
                if (n.isEmpty()) {
                    // Nuvio needs the name for its stream picker.
                    n = Meta.lookup(imdbId)?.name.orEmpty()
                    if (n.isEmpty()) error("Couldn't find this title's name. Type it in, then try again.")
                    name = n
                }
                val title = Title(imdbId.trim(), n, series, seasonN ?: 1, episodeN ?: 1)
                TvLink.open(context, host, title)
                prefs.edit().putString("host", host.trim()).putString("tvName", tvName).apply()
                status = "Opened “${title.name}” in Nuvio on ${tvName.ifEmpty { "the TV" }}."
            } catch (e: Exception) {
                failed = true
                status = "Couldn't open it: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    // A shared IMDb page fills in the id (and the title, when the share says it).
    if (shared != null) {
        imdbId = shared.imdbId
        if (shared.name.isNotEmpty()) {
            name = shared.name
            autoName = true
        }
        shared.series?.let { series = it }
        status = null
        failed = false
        openWhenKnown = true
        shareRun++
        onSharedUsed()
    }

    // The id gives the title and whether it's a movie or a series (Cinemeta).
    LaunchedEffect(imdbId, shareRun) {
        notFound = false
        if (!Title.isImdbId(imdbId)) return@LaunchedEffect
        lookingUp = true
        delay(300)      // typing: wait for the whole id
        val found = Meta.lookup(imdbId)
        lookingUp = false
        if (found == null) {
            notFound = true
            openWhenKnown = false
            return@LaunchedEffect
        }
        if (autoName || name.isBlank()) {
            name = found.name
            autoName = true
        }
        series = found.series
        if (openWhenKnown) {
            openWhenKnown = false
            when {
                found.series -> status = "Pick the season and episode, then Open on TV."
                hostOk && !busy -> openOnTv()
                else -> status = "Pick your TV, then Open on TV."
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Nuvio Remote") }) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nearby TVs", style = MaterialTheme.typography.titleSmall)
            if (tvs.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                    }
                    Text(
                        if (searching) "Looking for TVs on your Wi-Fi…" else "No TV found on this Wi-Fi. Enter its address below.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            for (tv in tvs) {
                val selected = tv.ready && tv.host == host.trim()
                Card(
                    onClick = {
                        if (tv.ready) {
                            host = tv.host
                            tvName = tv.name
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tv.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (tv.ready) tv.host else "${tv.host} · turn on USB debugging on this TV",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) Text("✓", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            if (!searching) {
                TextButton(onClick = { finder.start(); searchRun++ }) { Text("Search again") }
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it; tvName = "" },
                label = { Text("TV address") },
                placeholder = { Text("192.168.1.7") },
                supportingText = { Text("Picked from the list, or Settings → Network & Internet on the TV") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !series, onClick = { series = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Movie") }
                SegmentedButton(
                    selected = series, onClick = { series = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Series") }
            }

            OutlinedTextField(
                value = imdbId,
                onValueChange = { imdbId = Title.idIn(it) ?: it.trim() },     // a pasted IMDb link works too
                label = { Text("IMDb ID or link") },
                placeholder = { Text("tt0120737") },
                supportingText = { Text("Or share an IMDb page to this app (from Chrome or the IMDb app)") },
                isError = imdbId.isNotEmpty() && !idOk,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; autoName = false },
                label = { Text("Title") },
                placeholder = { Text("Filled in from the IMDb ID") },
                supportingText = {
                    Text(
                        when {
                            lookingUp -> "Looking it up…"
                            notFound && name.isBlank() -> "Not found on Cinemeta: type the title"
                            else -> "Nuvio shows this name; you can change it"
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (series) ImeAction.Next else ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (series) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = season, onValueChange = { season = it.filter(Char::isDigit) },
                        label = { Text("Season") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = episode, onValueChange = { episode = it.filter(Char::isDigit) },
                        label = { Text("Episode") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Button(
                onClick = { openOnTv() },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                }
                Text("Open on TV")
            }
            if (!ready && !busy) {
                // What's still missing, rather than a gray button and no reason.
                val missing = listOfNotNull(
                    "pick a TV".takeIf { !hostOk },
                    "enter an IMDb ID or link (or share an IMDb page)".takeIf { !idOk },
                    "the season and episode".takeIf { !episodeOk },
                )
                Text(
                    "To open: " + missing.joinToString(", ") + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            status?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(
                "Needs USB debugging on the TV (Settings → System → Developer options), and the phone on the same Wi-Fi. " +
                    "Nuvio opens the title's stream picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
