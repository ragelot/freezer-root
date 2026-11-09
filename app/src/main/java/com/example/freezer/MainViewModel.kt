package com.example.freezer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Filter { ALL, FROZEN, ACTIVE }

data class UiState(
    val loading: Boolean = true,
    val rootOk: Boolean? = null,
    val apps: List<AppItem> = emptyList(),
    val query: String = "",
    val filter: Filter = Filter.ALL,
    val showSystem: Boolean = false,
    val message: String? = null,
) {
    val visible: List<AppItem>
        get() = apps.filter { a ->
            (showSystem || !a.isSystem) &&
                (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true)) &&
                when (filter) {
                    Filter.ALL -> true
                    Filter.FROZEN -> a.frozen
                    Filter.ACTIVE -> !a.frozen
                }
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val root = withContext(Dispatchers.IO) { RootShell.isRootAvailable() }
            _state.update { it.copy(rootOk = root) }
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val apps = repo.loadApps()
        _state.update { it.copy(apps = apps, loading = false) }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }
    fun toggleSystem() = _state.update { it.copy(showSystem = !it.showSystem) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun forceStop(item: AppItem) = viewModelScope.launch {
        val ok = repo.forceStop(item.packageName)
        showMessage(if (ok) "${item.label} arrêtée" else "Échec : vérifie l'accès root")
    }

    fun clearCache(item: AppItem) = viewModelScope.launch {
        val ok = repo.clearCache(item.packageName)
        showMessage(if (ok) "Cache de ${item.label} vidé" else "Échec : vérifie l'accès root")
    }

    fun toggleFreeze(item: AppItem) = viewModelScope.launch {
        val target = !item.frozen
        val ok = repo.setFrozen(item.packageName, target)
        if (ok) {
            _state.update { s ->
                s.copy(apps = s.apps.map { if (it.packageName == item.packageName) it.copy(frozen = target) else it })
            }
        } else {
            _state.update { it.copy(message = "Échec : vérifie l'accès root") }
        }
    }

    fun freezeAllActive(items: List<AppItem>) = viewModelScope.launch {
        items.filter { !it.frozen }.forEach { toggleFreeze(it).join() }
    }
}
