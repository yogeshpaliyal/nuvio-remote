package dev.skiff

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.skiff.atv.Atv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** The last IMDb page shared to us, read by the screen (null = nothing new). */
    private var shared by mutableStateOf<Title.Shared?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Rules.init(this)
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

/** Material You colors (from the wallpaper, Android 12+) with Material 3 Expressive motion and shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}

private const val PREFS = "remote"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteScreen(shared: Title.Shared?, onSharedUsed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    // History and Saved for later.
    val library = remember { Library.get(context) }
    val entries by library.entries.collectAsState()
    var showLibrary by remember { mutableStateOf(false) }
    var showRemote by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current

    // The TV
    var host by remember { mutableStateOf(prefs.getString("host", "").orEmpty()) }
    // The TV picked from the list, by name: if the router gives it a new address, we follow it.
    var tvName by remember { mutableStateOf(prefs.getString("tvName", "").orEmpty()) }
    var tvs by remember { mutableStateOf(emptyList<TvFinder.Tv>()) }
    var searching by remember { mutableStateOf(true) }
    var searchRun by remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    // The Nuvio apps on that TV (Play Store and GitHub builds, forks), and the one to open titles in.
    var apps by remember { mutableStateOf(emptyList<TvLink.NuvioApp>()) }
    var appsLoading by remember { mutableStateOf(false) }
    var appsError by remember { mutableStateOf<String?>(null) }
    var appsRun by remember { mutableIntStateOf(0) }
    var appComponent by remember { mutableStateOf(prefs.getString("app", null)) }
    val finder = remember {
        TvFinder(context) { found ->
            tvs = found
            // Usable: USB debugging on, or paired (the Google TV remote protocol).
            fun usable(tv: TvFinder.Tv) = tv.ready || Atv.isPaired(context, tv.host)
            val same = found.firstOrNull { usable(it) && it.name == tvName }
            when {
                same != null -> host = same.host
                host.isBlank() -> found.singleOrNull { usable(it) }?.let { host = it.host; tvName = it.name }
            }
        }
    }
    // Newer link rules from the repo, at most once a day.
    LaunchedEffect(Unit) { Rules.refresh(context) }
    DisposableEffect(Unit) {
        finder.start()
        onDispose { finder.stop() }
    }
    LaunchedEffect(searchRun) {
        searching = true
        delay(8_000)
        searching = false
    }

    // The title
    var series by remember { mutableStateOf(false) }
    var imdbId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var meta by remember { mutableStateOf<Meta.Found?>(null) }
    var season by remember { mutableIntStateOf(1) }
    var episode by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    // The title was filled in for you (from the share or the lookup): a later lookup may replace it; yours, not.
    var autoName by remember { mutableStateOf(true) }
    var editName by remember { mutableStateOf(false) }
    var lookingUp by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    // Shared a page: open it on the TV as soon as it's known to be a movie (a series waits for its episode).
    var openWhenKnown by remember { mutableStateOf(false) }
    var shareRun by remember { mutableIntStateOf(0) }     // the same page shared again looks it up again
    // Movie or series set by you (the toggle, or an entry you edited): the lookup doesn't change it back.
    var keepType by remember { mutableStateOf(false) }
    // Or any other link (JioHotstar, YouTube…): opened on the TV by the app that opens such links.
    var link by remember { mutableStateOf<String?>(null) }
    var linkTitle by remember { mutableStateOf("") }       // what the share called it, until the page says
    // A link goes to history when shared, pasted or opened from it (or played): not while it's being typed.
    var recordLink by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Links.Page?>(null) }
    var linkApps by remember { mutableStateOf(emptyList<TvLink.LinkApp>()) }
    var linkAppsLoading by remember { mutableStateOf(false) }
    var linkComponent by remember { mutableStateOf(prefs.getString("linkApp", null)) }
    var linkRun by remember { mutableIntStateOf(0) }     // "Check again"

    val hostOk = host.trim().isNotEmpty()
    val idOk = Title.isImdbId(imdbId)
    val linkOk = link != null
    // Paired with this TV: links open over the remote protocol, not ADB (the TV picks the app). Checked again when
    // the remote closes (pairing happens there).
    val paired = remember(host.trim(), showRemote) { hostOk && Atv.isPaired(context, host) }
    val linkByPairing = linkOk && paired
    val ready = hostOk && (idOk || (linkOk && (linkApps.isNotEmpty() || linkByPairing))) && !busy
    val tvLabel = tvName.ifEmpty { host.trim().ifEmpty { "the TV" } }

    // A TV picked: which Nuvio apps it has (connecting is also when it asks to allow this phone, the first time).
    LaunchedEffect(host.trim(), appsRun) {
        apps = emptyList()
        appsError = null
        if (host.isBlank()) return@LaunchedEffect
        delay(500)      // typing an address: wait for the whole of it
        appsLoading = true
        try {
            val found = TvLink.apps(context, host)
            apps = found
            if (found.none { it.component == appComponent }) appComponent = found.firstOrNull()?.component
            if (found.isEmpty()) appsError = "There's no Nuvio app on $tvLabel. Install it from the Play Store or GitHub."
        } catch (e: Exception) {
            appsError = if (Atv.isPaired(context, host)) {
                "Opening titles in Nuvio needs USB debugging on $tvLabel (links and the remote work paired)."
            } else {
                "Couldn't reach $tvLabel: ${e.message ?: e.javaClass.simpleName}"
            }
        } finally {
            appsLoading = false
        }
    }

    fun openLinkOnTv() {
        val l = link ?: return
        busy = true
        failed = false
        val byPairing = linkByPairing
        status = if (byPairing) "Connecting…" else "Connecting… The first time, accept “Allow USB debugging?” on the TV (tick Always allow)."
        scope.launch {
            try {
                if (byPairing) Atv.openLink(context, host, Links.forTv(l)) else TvLink.openLink(context, host, l, linkComponent)
                val app = if (byPairing) null else (linkApps.firstOrNull { it.component == linkComponent } ?: linkApps.firstOrNull())?.name
                val p = page ?: Links.Page(linkTitle.ifBlank { Links.site(l) })
                library.useLink(l, p, app, played = true)
                prefs.edit().putString("host", host.trim()).putString("tvName", tvName).putString("linkApp", linkComponent).apply()
                status = "Opened “${p.title}”" + (app?.let { " in $it" } ?: "") + " on $tvLabel."
            } catch (e: Exception) {
                failed = true
                status = "Couldn't open it: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun openOnTv() {
        if (link != null) return openLinkOnTv()
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
                val title = Title(imdbId.trim(), n, series, season, episode)
                TvLink.open(context, host, title, appComponent)
                library.use(title, meta, played = true)
                prefs.edit().putString("host", host.trim()).putString("tvName", tvName).putString("app", appComponent).apply()
                status = "Playing “${title.name}”" + (if (series) " S$season E$episode" else "") + " on $tvLabel."
            } catch (e: Exception) {
                failed = true
                status = "Couldn't open it: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    /** An entry from the library back in the remote, as it was used (or edited); `play`: straight to the TV. */
    fun pick(e: Library.Entry, play: Boolean) {
        showLibrary = false
        if (e.link != null) {
            link = e.link
            linkTitle = e.name
            recordLink = true
            imdbId = ""
            failed = false
            status = null
            // Played once the TV says which app opens it (the link effect).
            openWhenKnown = play
            shareRun++
            return
        }
        link = null
        imdbId = e.imdbId
        name = e.name
        autoName = false
        series = e.series
        keepType = true
        season = e.season
        episode = e.episode
        editName = false
        failed = false
        status = null
        if (play) {
            if (hostOk && !busy) openOnTv() else status = "Pick your TV, then play it."
        }
    }

    // A shared IMDb page fills in the id (and the title, when the share says it).
    if (shared != null && shared.link != null) {
        link = shared.link
        linkTitle = shared.name
        recordLink = true
        imdbId = ""
        status = null
        failed = false
        openWhenKnown = true
        shareRun++
        onSharedUsed()
    } else if (shared != null) {
        link = null
        imdbId = shared.imdbId
        if (shared.name.isNotEmpty()) {
            name = shared.name
            autoName = true
        }
        shared.series?.let { series = it }
        keepType = false
        status = null
        failed = false
        openWhenKnown = true
        shareRun++
        onSharedUsed()
    }

    // A link: its name and picture (the page, or the link itself), and which TV apps open it; shared: opened as soon
    // as there's an app for it.
    LaunchedEffect(link, shareRun) {
        page = null
        val l = link ?: return@LaunchedEffect
        if (!recordLink) delay(600)     // typing: wait for the whole link
        val p = Links.page(l, linkTitle)
        page = p
        if (recordLink) library.useLink(l, p, null, played = false)
    }
    LaunchedEffect(link, host.trim(), shareRun, linkRun, paired) {
        linkApps = emptyList()
        val l = link ?: return@LaunchedEffect
        if (host.isBlank()) return@LaunchedEffect
        if (paired) {
            // Over pairing: no app to look up, the TV picks it.
            if (openWhenKnown) {
                openWhenKnown = false
                if (!busy) openLinkOnTv()
            }
            return@LaunchedEffect
        }
        delay(300)
        linkAppsLoading = true
        try {
            val found = TvLink.linkApps(context, host, l)
            linkApps = found
            if (found.none { it.component == linkComponent }) linkComponent = found.firstOrNull()?.component
        } catch (e: Exception) {
            failed = true
            status = "Couldn't reach $tvLabel: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            linkAppsLoading = false
        }
        if (openWhenKnown) {
            openWhenKnown = false
            if (linkApps.isNotEmpty() && !busy) openLinkOnTv()
        }
    }

    // The id gives the title, its art and whether it's a movie or a series (Cinemeta).
    LaunchedEffect(imdbId, shareRun) {
        notFound = false
        if (!Title.isImdbId(imdbId)) {
            meta = null
            return@LaunchedEffect
        }
        lookingUp = true
        delay(300)      // typing: wait for the whole id
        val found = Meta.lookup(imdbId)
        lookingUp = false
        meta = found
        if (found == null) {
            notFound = true
            editName = true
            openWhenKnown = false
            return@LaunchedEffect
        }
        if (autoName || name.isBlank()) {
            name = found.name
            autoName = true
        }
        if (!keepType) series = found.series
        library.use(Title(imdbId, name, series, season, episode), found, played = false)
        if (openWhenKnown) {
            openWhenKnown = false
            when {
                found.series -> status = "Pick the season and episode, then play it."
                hostOk && !busy -> openOnTv()
                else -> status = "Pick your TV, then play it."
            }
        }
    }

    BackHandler(enabled = showLibrary || showRemote) { showLibrary = false; showRemote = false }
    // 0: send, 1: library, 2: remote
    AnimatedContent(targetState = if (showLibrary) 1 else if (showRemote) 2 else 0, label = "screen") { screen ->
    if (screen == 1) {
        LibraryScreen(library, onBack = { showLibrary = false }, onOpen = { pick(it, false) }, onPlay = { pick(it, true) })
        return@AnimatedContent
    }
    if (screen == 2) {
        RemoteScreen(host.trim(), tvName, onBack = { showRemote = false })
        return@AnimatedContent
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Skiff") },
                subtitle = {
                    Text(
                        when {
                            hostOk && tvs.any { it.ready && it.host == host.trim() } -> "$tvLabel · ready"
                            hostOk -> tvLabel
                            searching -> "Looking for TVs…"
                            else -> "No TV picked"
                        },
                    )
                },
                actions = {
                    IconButton(onClick = { showRemote = true }, enabled = hostOk) { Icon(AppIcons.Remote, "Remote") }
                    IconButton(onClick = { showLibrary = true }) { Icon(AppIcons.History, "History and saved") }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            PlayBar(
                label = if (hostOk) "Play on $tvLabel" else "Play on TV",
                enabled = ready,
                busy = busy,
                hint = when {
                    busy -> null
                    !hostOk -> "Pick a TV above"
                    linkOk && paired -> null
                    linkOk && linkAppsLoading -> "Checking which TV app opens it…"
                    linkOk && linkApps.isEmpty() && !paired -> "No app on $tvLabel opens ${link?.let(Links::site)} links"
                    !idOk && !linkOk -> "Share a page (IMDb, JioHotstar, YouTube…), or paste its link"
                    else -> null
                },
                onClick = { openOnTv() },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // MARK: Your TV
            SectionHeader("Your TV") {
                if (!searching) {
                    TextButton(onClick = { finder.start(); searchRun++ }) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search again")
                    }
                }
            }
            if (tvs.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (searching) LoadingIndicator(Modifier.size(40.dp))
                        else Icon(AppIcons.Tv, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (searching) "Looking for TVs on your Wi-Fi…" else "No TV found on this Wi-Fi. Enter its address.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            for (tv in tvs) {
                TvCard(
                    tv = tv,
                    selected = tv.host == host.trim(),
                    paired = Atv.isPaired(context, tv.host),
                    onClick = {
                        // Without USB debugging too: it can be paired (the remote shows how).
                        host = tv.host
                        tvName = tv.name
                        manual = false
                    },
                )
            }
            AnimatedVisibility(visible = manual || (!searching && tvs.none { it.ready })) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; tvName = "" },
                    label = { Text("TV address") },
                    placeholder = { Text("192.168.1.7") },
                    supportingText = { Text("On the TV: Settings → Network & Internet") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!manual && tvs.any { it.ready }) {
                TextButton(onClick = { manual = true }) { Text("Enter an address instead") }
            }

            // MARK: Open with (a link: the TV apps that open it)
            if (hostOk && linkOk) {
                SectionHeader("Open with") {
                    if (!linkAppsLoading && !paired) {
                        TextButton(onClick = { linkRun++ }) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Check again")
                        }
                    }
                }
                when {
                    linkAppsLoading -> Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(40.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Checking which app on $tvLabel opens it…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    paired -> NoteCard(
                        Icons.Rounded.Check,
                        "Paired with $tvLabel: it opens the link in its app for ${link?.let(Links::site)}.",
                        error = false,
                    )
                    linkApps.isEmpty() -> NoteCard(
                        Icons.Rounded.Warning,
                        "No app on $tvLabel opens ${link?.let(Links::site)} links. Install its TV app, then try again.",
                        error = true,
                    )
                    else -> for (app in linkApps) {
                        AppCard(
                            app.name, app.pkg,
                            selected = app.component == linkComponent, onClick = { linkComponent = app.component },
                        )
                    }
                }
            }

            // MARK: Open with (an IMDb title: the Nuvio apps)
            if (hostOk && !linkOk) {
                SectionHeader("Open with") {
                    if (!appsLoading) {
                        TextButton(onClick = { appsRun++ }) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Check again")
                        }
                    }
                }
                when {
                    appsLoading -> Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(40.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "Checking $tvLabel's apps… The first time, allow USB debugging on the TV.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    appsError != null -> NoteCard(Icons.Rounded.Warning, appsError!!, error = true)
                    else -> for (app in apps) {
                        AppCard(
                            listOfNotNull(app.name, app.version).joinToString(" · "), "${app.source} · ${app.pkg}",
                            selected = app.component == appComponent, onClick = { appComponent = app.component },
                        )
                    }
                }
            }

            // MARK: What to watch
            SectionHeader("What to watch")
            OutlinedTextField(
                value = link ?: imdbId,
                onValueChange = { v ->
                    // An IMDb link or id: the Nuvio flow; any other link: the app on the TV that opens it.
                    val id = Title.idIn(v)
                    when {
                        id != null -> { link = null; imdbId = id; keepType = false }
                        Links.isLink(v.trim()) -> { link = v.trim(); linkTitle = ""; recordLink = false; imdbId = "" }
                        else -> { link = null; imdbId = v.trim(); keepType = false }
                    }
                },
                label = { Text("Link or IMDb ID") },
                placeholder = { Text("tt0120737, or a JioHotstar / YouTube link") },
                supportingText = { Text("Or share a page here: IMDb, JioHotstar, YouTube…") },
                isError = imdbId.isNotEmpty() && !idOk && !linkOk,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.let { t ->
                            val id = Title.idIn(t)
                            val l = Links.linkIn(t)
                            when {
                                id != null -> { link = null; imdbId = id; autoName = true; keepType = false }
                                l != null -> { link = l; linkTitle = ""; recordLink = true; imdbId = "" }
                            }
                        }
                    }) { Text("Paste") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            // Nothing entered: the last few used, one tap from the TV.
            val recent = entries.filter { it.inHistory }.take(3)
            if (!idOk && !linkOk && recent.isNotEmpty()) {
                SectionHeader("Recent") {
                    TextButton(onClick = { showLibrary = true }) { Text("See all") }
                }
                for (e in recent) {
                    EntryRow(
                        e,
                        onOpen = { pick(e, false) },
                        onPlay = { pick(e, true) },
                        onToggleSaved = { library.setSaved(e.title(), null, !e.saved) },
                    )
                }
            }

            if (linkOk) {
                val l = link!!
                LinkCard(page, Links.site(l), loading = page == null)
                val savedNow = entries.any { it.imdbId == l && it.saved }
                ToggleButton(
                    checked = savedNow,
                    onCheckedChange = { on ->
                        library.setLinkSaved(l, page, linkApps.firstOrNull { it.component == linkComponent }?.name, on)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (savedNow) AppIcons.Bookmark else AppIcons.BookmarkBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (savedNow) "Saved for later" else "Save for later")
                }
            }

            AnimatedContent(targetState = Triple(meta, lookingUp, idOk), label = "title") { (m, looking, valid) ->
                when {
                    looking -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                    m != null -> TitleCard(m)
                    valid && notFound -> NoteCard(Icons.Rounded.Warning, "Not on Cinemeta. Type the title below.", error = true)
                    else -> Spacer(Modifier.height(0.dp))
                }
            }

            if (idOk && (meta != null || name.isNotBlank())) {
                val savedNow = entries.any { it.imdbId == imdbId && it.saved }
                ToggleButton(
                    checked = savedNow,
                    onCheckedChange = { on ->
                        library.setSaved(Title(imdbId, name.ifBlank { meta?.name.orEmpty() }, series, season, episode), meta, on)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (savedNow) AppIcons.Bookmark else AppIcons.BookmarkBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (savedNow) "Saved for later" else "Save for later")
                }
            }

            if (idOk) {
                // Movie or series: set by the lookup, yours to change.
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ToggleButton(
                        checked = !series,
                        onCheckedChange = { series = false; keepType = true },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(AppIcons.Movie, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Movie")
                    }
                    ToggleButton(
                        checked = series,
                        onCheckedChange = { series = true; keepType = true },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(AppIcons.Series, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Series")
                    }
                }
            }

            AnimatedVisibility(visible = idOk && series, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stepper("Season", season, { season = it }, Modifier.weight(1f))
                    Stepper("Episode", episode, { episode = it }, Modifier.weight(1f))
                }
            }

            if (idOk) {
                AnimatedVisibility(visible = editName || notFound) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; autoName = false },
                        label = { Text("Name shown in Nuvio") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!editName && !notFound) {
                    TextButton(onClick = { editName = true }) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit the name")
                    }
                }
            }

            AnimatedContent(targetState = status to failed, label = "status") { (s, bad) ->
                if (s != null) NoteCard(if (bad) Icons.Rounded.Warning else Icons.Rounded.Info, s, error = bad)
            }

            Text(
                "Needs USB debugging on the TV (Settings → System → Developer options) and the phone on the same Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    }
}

@Composable
private fun SectionHeader(text: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
        )
        action()
    }
}

/** A TV: its name and address; the selected one gets the expressive cookie badge and the primary container. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TvCard(tv: TvFinder.Tv, selected: Boolean, paired: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "tv",
    )
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(if (selected) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Circle.toShape())
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Tv, null, Modifier.size(26.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        tv.ready -> tv.host
                        paired -> "${tv.host} · paired"
                        else -> "${tv.host} · pair it from the remote"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A TV app to open in: a Nuvio build (version, where it came from), or the app for a link; the picked one gets the cookie badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "app",
    )
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(if (selected) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Circle.toShape())
                    .background(if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow, null, Modifier.size(28.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** A link: its picture (when the page has one), its name, and the site. */
@Composable
private fun LinkCard(page: Links.Page?, site: String, loading: Boolean) {
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
            val image = page?.image
            if (image != null) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.35f))),
                    ),
                )
            }
            val onArt = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    LoadingIndicator(Modifier.size(40.dp))
                } else {
                    Text(
                        page?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = onArt, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(site)
                    page?.by?.let { Pill(it) }
                }
            }
        }
    }
}

/** The title from Cinemeta: its backdrop behind, the poster, then year · genres · rating · runtime. */
@Composable
private fun TitleCard(m: Meta.Found) {
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
            if (m.background != null) {
                AsyncImage(
                    model = m.background, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.35f))),
                    ),
                )
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.poster != null) {
                    AsyncImage(
                        model = m.poster, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                val onArt = if (m.background != null) Color.White else MaterialTheme.colorScheme.onSurface
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        m.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = onArt, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    val details = listOfNotNull(m.year, m.genres.takeIf { it.isNotEmpty() }?.joinToString(", "), m.runtime)
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = onArt.copy(alpha = 0.85f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(if (m.series) "Series" else "Movie")
                        m.rating?.let { Pill("★ $it") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Season or episode: − number +. */
@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { if (value > 1) onChange(value - 1) }, enabled = value > 1) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                AnimatedContent(targetState = value, label = label) { v ->
                    Text(
                        "$v", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                FilledTonalIconButton(onClick = { onChange(value + 1) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun NoteCard(icon: ImageVector, text: String, error: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The one action, always at hand: a large expressive button (its shape morphs when pressed). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayBar(label: String, enabled: Boolean, busy: Boolean, hint: String?, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val height = ButtonDefaults.LargeContainerHeight
            Button(
                onClick = onClick,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(ButtonDefaults.iconSizeFor(height)), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
                }
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                Text(label, style = ButtonDefaults.textStyleFor(height), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hint != null) {
                Text(
                    hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
