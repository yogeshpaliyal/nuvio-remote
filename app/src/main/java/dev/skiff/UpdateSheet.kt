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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A newer Skiff: what's new (the release's notes) and Update (Google Play, or the release's APK). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(r: Updates.Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fromPlay = Updates.viaPlay
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (r.version.isNotBlank()) "Skiff ${r.version}" else "A new Skiff", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "You have ${Updates.current(context)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (r.notes.isNotBlank()) Notes(r.notes)
                else Text("What's new is on Skiff's Google Play page.", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (fromPlay) "Google Play downloads it while you use Skiff, then Skiff restarts into it. Your history stays."
                else "Downloads the APK in your browser: open it to install. Your history stays.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { Updates.later(context, r); onDismiss() }) { Text("Later") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { Updates.open(context, r); onDismiss() }) { Text("Update") }
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
