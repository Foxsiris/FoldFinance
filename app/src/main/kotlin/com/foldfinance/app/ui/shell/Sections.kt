package com.foldfinance.app.ui.shell

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.R
import com.foldfinance.app.ui.analytics.AnalyticsRoute
import com.foldfinance.app.ui.history.HistoryRoute
import com.foldfinance.app.ui.overview.OverviewRoute
import com.foldfinance.app.ui.review.ReviewRoute
import com.foldfinance.app.ui.settings.SettingsRoute
import com.foldfinance.app.ui.subscriptions.SubscriptionsRoute

/**
 * Top-level sections of the app, in navigation order. The same order on both screens; the outer
 * screen's bar leaves out [Subscriptions] (see [barSections]).
 */
enum class Section(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Overview(R.string.section_overview, Icons.Outlined.Home, Icons.Filled.Home),
    History(R.string.section_history, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Review(R.string.section_review, Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
    Analytics(R.string.section_analytics, Icons.Outlined.Insights, Icons.Filled.Insights),
    Subscriptions(R.string.section_subscriptions, Icons.Outlined.Autorenew, Icons.Filled.Autorenew),
    Settings(R.string.section_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/**
 * The one place that decides which sections are finished.
 *
 * CLAUDE.md: no placeholder buttons in release — a section that is not ready is hidden from the
 * release navigation and visible only in debug builds. When a stage finishes a section, add it
 * to [ready].
 */
object Sections {
    /** Sections with real, release-quality content. */
    val ready: Set<Section> = Section.entries.toSet()

    fun isReady(section: Section): Boolean = section in ready

    /** Sections shown in navigation: everything in debug, only [ready] ones in release. */
    fun visible(debug: Boolean = BuildConfig.DEBUG): List<Section> =
        Section.entries.filter { debug || isReady(it) }
}

/**
 * Most items a bottom bar of a Compact window shows: with six, labels like «Аналитика» are cut
 * on a 337 dp outer screen (Material: 3–5 destinations).
 */
const val MaxCompactBarItems: Int = 5

/**
 * Sections that live outside the Compact bar. They stay reachable — «Подписки» by the card on
 * «Обзор» and the row in «Настройки» — and stay selectable: a section selected on the inner
 * screen is still shown after folding, just with no bar item highlighted.
 */
val SectionsOutsideCompactBar: Set<Section> = setOf(Section.Subscriptions)

/**
 * Navigation items for [compactBar] (bottom bar of a Compact window) or the rail / wide bar.
 * The rail shows everything; the Compact bar drops [SectionsOutsideCompactBar] and never has more
 * than [MaxCompactBarItems].
 */
fun barSections(sections: List<Section>, compactBar: Boolean): List<Section> =
    if (!compactBar) sections else sections.filterNot { it in SectionsOutsideCompactBar }.take(MaxCompactBarItems)

/** Entry point of each section. Each route file is owned by the agent of that feature. */
@Composable
fun SectionContent(section: Section, modifier: Modifier = Modifier) {
    when (section) {
        Section.Overview -> OverviewRoute(modifier)
        Section.History -> HistoryRoute(modifier)
        Section.Analytics -> AnalyticsRoute(modifier)
        Section.Review -> ReviewRoute(modifier)
        Section.Subscriptions -> SubscriptionsRoute(modifier)
        Section.Settings -> SettingsRoute(modifier)
    }
}
