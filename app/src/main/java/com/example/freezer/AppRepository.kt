package com.example.freezer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean,
    val frozen: Boolean,
)

class AppRepository(private val context: Context) {

    suspend fun loadApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter { it.packageName != context.packageName }
            .map { info ->
                AppItem(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    frozen = !info.enabled,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun setFrozen(pkg: String, freeze: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cmd = if (freeze) {
            "pm disable-user --user 0 $pkg && am force-stop $pkg"
        } else {
            "pm enable $pkg"
        }
        RootShell.exec(cmd).ok
    }

    /** Arrête immédiatement l'application (sans la geler). */
    suspend fun forceStop(pkg: String): Boolean = withContext(Dispatchers.IO) {
        RootShell.exec("am force-stop $pkg").ok
    }

    /** Vide le cache de l'application (cache et code_cache). */
    suspend fun clearCache(pkg: String): Boolean = withContext(Dispatchers.IO) {
        RootShell.exec(
            "rm -rf /data/data/$pkg/cache/* /data/data/$pkg/code_cache/* " +
                "/data/user_de/0/$pkg/cache/* /data/user_de/0/$pkg/code_cache/*"
        ).ok
    }
}
