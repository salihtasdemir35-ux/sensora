package com.sensora.app.ui.history

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensora.app.R
import com.sensora.app.core.model.Quality
import com.sensora.app.data.Exporters
import com.sensora.app.data.MeasurementRecord
import com.sensora.app.ui.catalog.Category
import com.sensora.app.ui.catalog.ToolCatalog
import com.sensora.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.sensoraApp
    val scope = rememberCoroutineScope()
    val records by remember { app.history.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf(2) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val exportedMsg = stringResource(R.string.history_exported)

    val now = System.currentTimeMillis()
    val filtered = records.filter { r ->
        (category == null || r.category == category) &&
            (!favoritesOnly || r.favorite) &&
            when (period) { 0 -> now - r.timestamp < 86_400_000L; 1 -> now - r.timestamp < 7 * 86_400_000L; else -> true }
    }

    fun export(uri: android.net.Uri?, content: () -> String) {
        if (uri == null) return
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(content().toByteArray()) } }.isSuccess
            withContext(Dispatchers.Main) { if (ok) Toast.makeText(ctx, exportedMsg, Toast.LENGTH_SHORT).show() }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        export(uri) { Exporters.toCsv(filtered) { id -> ToolCatalog.byId(id)?.let { ctx.getString(it.title) } ?: id } }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        export(uri) { Exporters.toJson(records) }
    }

    SimpleScaffold(stringResource(R.string.history), onBack, actions = {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.history_export_csv)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.history_export_csv)) }, onClick = { menu = false; csvLauncher.launch("sensora.csv") })
            DropdownMenuItem(text = { Text(stringResource(R.string.history_export_json)) }, onClick = { menu = false; jsonLauncher.launch("sensora-backup.json") })
        }
        IconButton(onClick = { confirmClear = true }, enabled = records.isNotEmpty()) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ChoiceChips(listOf(stringResource(R.string.filter_today), stringResource(R.string.filter_week), stringResource(R.string.filter_all_time)), period) { period = it }
            val cats = listOf<Category?>(null) + Category.entries.filter { c -> records.any { it.category == c.name } }
            ChoiceChips(cats.map { if (it == null) stringResource(R.string.all) else stringResource(it.title) }, cats.indexOf(Category.entries.firstOrNull { it.name == category }).coerceAtLeast(0)) {
                category = cats[it]?.name
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = favoritesOnly, onCheckedChange = { favoritesOnly = it })
                Text(stringResource(R.string.favorites_only))
            }
            if (filtered.isEmpty()) {
                Text(stringResource(R.string.history_empty), modifier = Modifier.padding(24.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { r ->
                    HistoryItem(r,
                        onToggleFavorite = { scope.launch { app.history.update(r.copy(favorite = !r.favorite)) } },
                        onDelete = { scope.launch { app.history.delete(r) } })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = { TextButton(onClick = { scope.launch { app.history.clear() }; confirmClear = false }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
        )
    }
}

@Composable
private fun HistoryItem(r: MeasurementRecord, onToggleFavorite: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val tool = ToolCatalog.byId(r.toolId)
    val date = remember(r.timestamp) {
        DateFormat.getMediumDateFormat(ctx).format(Date(r.timestamp)) + " " + DateFormat.getTimeFormat(ctx).format(Date(r.timestamp))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tool?.let { stringResource(it.title) } ?: r.toolId, style = MaterialTheme.typography.titleSmall)
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (r.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = stringResource(R.string.favorites))
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
            Text("${fmt(r.value, 2)} ${r.unit}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            r.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            QualityBadge(runCatching { Quality.valueOf(r.quality) }.getOrDefault(Quality.LIMITED))
            r.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
