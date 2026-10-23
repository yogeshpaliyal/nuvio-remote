package dev.skiff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A newer Skiff: what's new (the release's notes) and Update (Google Play, or the release's APK). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(r: Updates.Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fromPlay = Updates.viaPlay
    val progress by Updates.progress.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (r.version.isNotBlank()) stringResource(R.string.skiff_version, r.version) else stringResource(R.string.new_skiff), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.you_have, Updates.current(context)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (r.notes.isNotBlank()) Notes(r.notes)
                else Text(stringResource(R.string.notes_on_play), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(
                    if (fromPlay) R.string.update_help_play
                    else if (Updates.canInstall(context)) R.string.update_help_apk
                    else R.string.update_help_allow,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Getting it: the download's progress, installing, or what went wrong.
            when (val p = progress) {
                is Updates.Progress.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.fraction != null) LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        p.fraction?.let { stringResource(R.string.downloading_percent, (it * 100).toInt()) } ?: stringResource(R.string.downloading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Updates.Progress.Installing -> Text(stringResource(R.string.installing_confirm), style = MaterialTheme.typography.bodySmall)
                is Updates.Progress.Problem -> Text(p.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                null -> {}
            }
            val busy = progress is Updates.Progress.Downloading || progress == Updates.Progress.Installing
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { Updates.later(context, r); onDismiss(); Telemetry.event(context, "update_later") }, enabled = !busy) { Text(stringResource(R.string.later)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        Telemetry.event(context, "update_start", "retry" to (progress is Updates.Progress.Problem))
                        scope.launch { Updates.open(context, r) }
                        if (fromPlay) onDismiss()
                    },
                    enabled = !busy,
                ) { Text(stringResource(if (progress is Updates.Progress.Problem) R.string.try_again else R.string.update)) }
            }
        }
    }
}

/**
 * The release notes' Markdown, kept simple: headings, bullets and paragraphs, emphasis marks dropped. The "Install"
 * section is for the GitHub page (the update button does it here), so it stops there.
 */
@Composable
private fun Notes(md: String) {
    val lines = md.lines()
    val install = lines.indexOfFirst { it.trim().startsWith("#") && it.trimStart('#', ' ').startsWith("Install", ignoreCase = true) }
    for (raw in if (install >= 0) lines.take(install) else lines) {
        val line = raw.trim().replace("**", "").replace("`", "")
        when {
            line.isBlank() -> Spacer(Modifier.padding(2.dp))
            line.startsWith("#") -> Text(
                line.trimStart('#').trim(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            line.startsWith("- ") || line.startsWith("* ") -> Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(line.drop(2), style = MaterialTheme.typography.bodyMedium)
            }
            else -> Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
