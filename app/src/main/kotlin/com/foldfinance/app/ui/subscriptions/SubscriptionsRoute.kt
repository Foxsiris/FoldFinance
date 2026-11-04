package com.foldfinance.app.ui.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foldfinance.app.ui.shell.DebugSectionPlaceholder
import com.foldfinance.app.ui.shell.Section

/**
 * «Подписки» — entry point of the section. Not ready yet: hidden from release navigation
 * ([com.foldfinance.app.ui.shell.Sections]), shows a debug note in debug builds.
 */
@Composable
fun SubscriptionsRoute(modifier: Modifier = Modifier) {
    DebugSectionPlaceholder(Section.Subscriptions, modifier)
}
