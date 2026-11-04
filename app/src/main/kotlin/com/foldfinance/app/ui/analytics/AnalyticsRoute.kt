package com.foldfinance.app.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foldfinance.app.ui.shell.DebugSectionPlaceholder
import com.foldfinance.app.ui.shell.Section

/**
 * «Аналитика» — entry point of the section. Not ready yet: hidden from release navigation
 * ([com.foldfinance.app.ui.shell.Sections]), shows a debug note in debug builds.
 */
@Composable
fun AnalyticsRoute(modifier: Modifier = Modifier) {
    DebugSectionPlaceholder(Section.Analytics, modifier)
}
