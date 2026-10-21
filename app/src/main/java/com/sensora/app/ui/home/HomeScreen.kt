@file:OptIn(ExperimentalMaterial3Api::class)

package com.sensora.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.ui.catalog.Availability
import com.sensora.app.ui.catalog.Category
import com.sensora.app.ui.catalog.DeviceCapabilities
import com.sensora.app.ui.catalog.Tool
import com.sensora.app.ui.catalog.ToolCatalog
import com.sensora.app.ui.common.sensoraApp
import com.sensora.app.ui.theme.BadColor
import com.sensora.app.ui.theme.LimitedColor

@Composable
fun AppLogo(size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF149AA8), Color(0xFF08323D)))),
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun HomeScreen(
    onOpenTool: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.sensoraApp.prefs
    val favorites by prefs.favorites.collectAsStateWithLifecycle()
    val recents by prefs.recents.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val availability = remember { ToolCatalog.tools.associate { it.id to DeviceCapabilities.availability(ctx, it) } }
    val summary = remember { DeviceCapabilities.keySensorSummary(ctx) }
    val tools = ToolCatalog.tools.filter { t ->
        (category == null || t.category.name == category) &&
            (query.isBlank() || ctx.getString(t.title).contains(query, ignoreCase = true) ||
                ctx.getString(t.description).contains(query, ignoreCase = true))
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            actions = {
                IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.history)) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) }
            },
        )
    }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ElevatedCard(onClick = onOpenSensors, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.device_status, summary.first, summary.second), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.device_status_open), style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
            if (favorites.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShortcutRow(stringResource(R.string.favorites), Icons.Filled.Star, ToolCatalog.tools.filter { it.id in favorites }, onOpenTool)
                }
            }
            if (recents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShortcutRow(stringResource(R.string.recents), Icons.Filled.Schedule, recents.mapNotNull { ToolCatalog.byId(it) }, onOpenTool)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.all)) })
                    Category.entries.forEach { c ->
                        FilterChip(
                            selected = category == c.name, onClick = { category = c.name },
                            label = { Text(stringResource(c.title)) },
                            leadingIcon = { Icon(c.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            items(tools, key = { it.id }) { t ->
                ToolCard(t, availability[t.id] ?: Availability.FULL, t.id in favorites,
                    onToggleFavorite = { prefs.toggleFavorite(t.id) }, onClick = { onOpenTool(t.id) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.planned_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.planned_body), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tools: List<Tool>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.forEach { t ->
                AssistChip(onClick = { onOpen(t.id) }, label = { Text(stringResource(t.title)) },
                    leadingIcon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        }
    }
}

@Composable
private fun ToolCard(tool: Tool, availability: Availability, favorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    val hardware = tool.hardware.map { stringResource(it.label) }.joinToString(", ")
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(tool.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(tool.category.title), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = stringResource(R.string.favorites),
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(tool.description), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.example_prefix, stringResource(tool.example)), style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic)
            Text(stringResource(R.string.needs, hardware), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(tool.reliability.label), style = MaterialTheme.typography.labelMedium)
            when (availability) {
                Availability.NONE -> Text(stringResource(R.string.not_on_device), color = BadColor, style = MaterialTheme.typography.labelMedium)
                Availability.FALLBACK -> Text(stringResource(R.string.alt_available), color = LimitedColor, style = MaterialTheme.typography.labelMedium)
                Availability.FULL -> Unit
            }
        }
    }
}
