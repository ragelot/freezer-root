package com.example.freezer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
                            Text(when (f) {
                                Filter.ALL -> "Toutes"
                                Filter.FROZEN -> "Gelées"
                                Filter.ACTIVE -> "Actives"
                            })
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
                        AppRow(app, enabled = state.rootOk == true) { vm.toggleFreeze(app) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppItem, enabled: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.alpha(if (app.frozen) 0.55f else 1f),
        leadingContent = {
            if (app.icon != null) {
                Image(app.icon, null, Modifier.size(44.dp))
            } else {
                Icon(Icons.Default.Android, null, Modifier.size(44.dp))
            }
        },
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
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
