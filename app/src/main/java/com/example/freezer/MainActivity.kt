package com.example.freezer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                FreezerScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezerScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selected by remember { mutableStateOf<AppItem?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Freezer") },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Actualiser") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.rootOk == false) {
                Card(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        "Accès root non disponible. Accorde le root à Freezer (Magisk/KernelSU) puis actualise.",
                        Modifier.padding(16.dp),
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Rechercher une application") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Filter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { vm.setFilter(f) },
                        label = {
                            Text(
                                when (f) {
                                    Filter.ALL -> "Toutes"
                                    Filter.FROZEN -> "Gelées"
                                    Filter.ACTIVE -> "Actives"
                                }
                            )
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("Système", style = MaterialTheme.typography.labelMedium)
                Switch(checked = state.showSystem, onCheckedChange = { vm.toggleSystem() })
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            enabled = state.rootOk == true,
                            onClick = { selected = app },
                            onToggle = { vm.toggleFreeze(app) },
                        )
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AppActionsSheet(
            app = app,
            rootOk = state.rootOk == true,
            onDismiss = { selected = null },
            onFreeze = {
                vm.toggleFreeze(app)
                selected = null
            },
            onForceStop = {
                vm.forceStop(app)
                selected = null
            },
            onClearCache = {
                vm.clearCache(app)
                selected = null
            },
            onOpen = {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    vm.showMessage("Impossible d'ouvrir ${app.label}" + if (app.frozen) " (gelée)" else "")
                }
                selected = null
            },
            onInfo = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${app.packageName}"),
                )
                context.startActivity(intent)
                selected = null
            },
        )
    }
}

@Composable
fun AppRow(app: AppItem, enabled: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier
            .alpha(if (app.frozen) 0.55f else 1f)
            .clickable(onClick = onClick),
        leadingContent = {
            if (app.icon != null) {
                Image(app.icon, null, Modifier.size(44.dp))
            } else {
                Icon(Icons.Default.Android, null, Modifier.size(44.dp))
            }
        },
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            IconToggleButton(checked = app.frozen, onCheckedChange = { onToggle() }, enabled = enabled) {
                Icon(
                    Icons.Default.AcUnit,
                    contentDescription = if (app.frozen) "Dégeler" else "Geler",
                    tint = if (app.frozen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: AppItem,
    rootOk: Boolean,
    onDismiss: () -> Unit,
    onFreeze: () -> Unit,
    onForceStop: () -> Unit,
    onClearCache: () -> Unit,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (app.icon != null) {
                    Image(app.icon, null, Modifier.size(40.dp))
                } else {
                    Icon(Icons.Default.Android, null, Modifier.size(40.dp))
                }
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
            SheetAction(Icons.Default.AcUnit, if (app.frozen) "Dégeler" else "Geler", rootOk, onFreeze)
            SheetAction(Icons.Default.Block, "Forcer l'arrêt", rootOk, onForceStop)
            SheetAction(Icons.Default.Delete, "Vider le cache", rootOk, onClearCache)
            SheetAction(Icons.Default.PlayArrow, "Ouvrir", true, onOpen)
            SheetAction(Icons.Default.Info, "Infos de l'application", true, onInfo)
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(text) },
    )
}
