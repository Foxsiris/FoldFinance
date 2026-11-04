package com.foldfinance.app.ingest.push

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.foldfinance.parser.KnownBankSources
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/**
 * An app that can be a notification source.
 *
 * @property isKnownBank listed in [KnownBankSources] — shown on top of the picker.
 * @property icon launcher icon, already rasterised; `null` falls back to a letter tile.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isKnownBank: Boolean,
    val icon: Bitmap? = null,
)

/** Installed launchable apps for the notification-source picker. */
fun interface InstalledAppsSource {
    suspend fun load(): List<InstalledApp>
}

/**
 * Lists apps with a launcher activity (QUERY_ALL_PACKAGES is declared for exactly this), without
 * our own package. Known banks first, then alphabetically by label (Russian collation).
 */
class PackageManagerAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledAppsSource {

    override suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val iconPx = (ICON_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }
        activities
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isKnownBank = info.packageName in KnownBankSources.allNotificationPackages,
                    icon = runCatching { pm.getApplicationIcon(info).toBitmap(iconPx, iconPx) }.getOrNull(),
                )
            }
            .toList()
            .let(::sortForPicker)
    }

    private companion object {
        const val ICON_DP = 40
    }
}

/** Known banks first, then by label; stable for equal labels. */
fun sortForPicker(apps: List<InstalledApp>): List<InstalledApp> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru"))
    return apps.sortedWith(
        compareByDescending<InstalledApp> { it.isKnownBank }
            .thenComparator { a, b -> collator.compare(a.label, b.label) }
            .thenBy { it.packageName },
    )
}
