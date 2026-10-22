package dev.skiff

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** History (everything used) and Saved for later: open, play, save, edit, remove (with Undo). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    library: Library,
    onBack: () -> Unit,
    onOpen: (Library.Entry) -> Unit,
    onPlay: (Library.Entry) -> Unit,
) {
    val context = LocalContext.current
    val entries by library.entries.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Library.Entry?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val history = entries.filter { it.inHistory }
    val saved = entries.filter { it.saved }
    val shown = if (tab == 0) history else saved

    fun remove(e: Library.Entry) {
        if (tab == 0) library.removeFromHistory(e.imdbId) else library.setSaved(e.title(), null, false)
        Telemetry.event(context, "remove_entry", "tab" to (if (tab == 0) "history" else "saved"))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                "Removed “${e.name}”" + if (tab == 0) " from history" else " from saved",
                actionLabel = "Undo", withDismissAction = true,
            )
            if (r == SnackbarResult.ActionPerformed) {
                library.restore(e)
                Telemetry.event(context, "undo_remove")
            }
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                LargeFlexibleTopAppBar(
                    title = { Text("Library") },
                    subtitle = { Text("${history.size} in history · ${saved.size} saved") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    scrollBehavior = scroll,
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("History") },
                        icon = { Icon(AppIcons.History, null) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Saved for later") },
                        icon = { Icon(AppIcons.Bookmark, null) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (tab == 0) AppIcons.History else AppIcons.BookmarkBorder, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (tab == 0) "Titles and links you share, paste or play show up here."
                        else "Tap the bookmark on a title to watch it later.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.imdbId }) { e ->
                    // Keyed: a removed-then-restored entry gets a fresh swipe state.
                    key(e.imdbId, e.lastUsed, e.saved, e.inHistory) {
                        SwipeToRemove(onRemove = { remove(e) }) {
                            EntryRow(
                                e,
                                onOpen = { onOpen(e) },
                                onPlay = { onPlay(e) },
                                onToggleSaved = {
                                    library.setSaved(e.title(), null, !e.saved)
                                    Telemetry.event(context, "save", "kind" to (if (e.link != null) "link" else "title"), "on" to !e.saved, "from" to "library")
                                },
                                onEdit = { editing = e },
                                onRemove = { remove(e) },
                                removeLabel = if (tab == 0) "Remove from history" else "Remove from saved",
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        EditDialog(e, onDismiss = { editing = null }, onSave = { library.edit(it); editing = null; Telemetry.event(context, "edit_entry") })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

/** One title: poster, name, what was used (episode) and when; Play, bookmark, and a menu (edit, remove). */
@Composable
fun EntryRow(
    e: Library.Entry,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove",
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(52.dp).height(78.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when { e.link != null -> AppIcons.Tv; e.series -> AppIcons.Series; else -> AppIcons.Movie }, null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (e.poster != null) {
                    AsyncImage(model = e.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        when {
                            e.link != null -> e.app ?: Links.site(e.link)
                            e.series -> "S${e.season} E${e.episode}"
                            else -> "Movie"
                        },
                        e.year,
                        DateUtils.getRelativeTimeSpanString(e.lastUsed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (e.plays > 0) {
                    Text(
                        if (e.plays == 1) "Played once" else "Played ${e.plays} times",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onToggleSaved) {
                Icon(
                    if (e.saved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                    if (e.saved) "Remove from saved" else "Save for later",
                    tint = if (e.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, "Play on TV") }
            if (onEdit != null || onRemove != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        onEdit?.let {
                            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; it() })
                        }
                        onRemove?.let {
                            DropdownMenuItem(text = { Text(removeLabel) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menu = false; it() })
                        }
                    }
                }
            }
        }
    }
}

/** Fix an entry by hand: the name Nuvio shows, movie or series, and the episode. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditDialog(e: Library.Entry, onDismiss: () -> Unit, onSave: (Library.Entry) -> Unit) {
    var name by remember { mutableStateOf(e.name) }
    var series by remember { mutableStateOf(e.series) }
    var season by remember { mutableStateOf(e.season.toString()) }
    var episode by remember { mutableStateOf(e.episode.toString()) }
    val s = season.toIntOrNull()
    val ep = episode.toIntOrNull()
    val ok = name.isNotBlank() && (!series || (s != null && s > 0 && ep != null && ep > 0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name shown in Nuvio") },
                    singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(),
                )
                if (e.link == null) Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ToggleButton(
                        checked = !series, onCheckedChange = { series = false },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(), modifier = Modifier.weight(1f),
                    ) { Text("Movie") }
                    ToggleButton(
                        checked = series, onCheckedChange = { series = true },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(), modifier = Modifier.weight(1f),
                    ) { Text("Series") }
                }
                if (series && e.link == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = season, onValueChange = { season = it.filter(Char::isDigit) }, label = { Text("Season") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = episode, onValueChange = { episode = it.filter(Char::isDigit) }, label = { Text("Episode") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(e.copy(name = name.trim(), series = series, season = if (series) s ?: 1 else 1, episode = if (series) ep ?: 1 else 1))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
