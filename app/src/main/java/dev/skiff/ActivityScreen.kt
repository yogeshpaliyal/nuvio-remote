package dev.skiff

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What Skiff sent to the TV, newest first: how it went (Pairing or USB debugging), when, and whether it worked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries = ActivityLog.entries(context)
    LaunchedEffect(Unit) { Telemetry.screen(context, "activity") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Settings") } },
                actions = { if (entries.isNotEmpty()) TextButton(onClick = { ActivityLog.clear(context) }) { Text("Clear") } },
            )
        },
    ) { inner ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "What Skiff sends to the TV shows here, with how it went: Pairing or USB debugging.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(entries, key = { it.time.toString() + it.what }) { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (e.ok) Color(0xFF3DDC84) else MaterialTheme.colorScheme.error),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.what, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                        Text(
                            e.via + (if (e.ok) "" else " · failed") + " · " + DateUtils.formatDateTime(
                                context, e.time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
