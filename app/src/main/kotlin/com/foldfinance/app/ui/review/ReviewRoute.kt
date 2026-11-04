package com.foldfinance.app.ui.review

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foldfinance.app.ui.shell.DebugSectionPlaceholder
import com.foldfinance.app.ui.shell.Section

/**
 * «Проверка» — entry point of the section. Not ready yet: hidden from release navigation
 * ([com.foldfinance.app.ui.shell.Sections]), shows a debug note in debug builds.
 */
@Composable
fun ReviewRoute(modifier: Modifier = Modifier) {
    DebugSectionPlaceholder(Section.Review, modifier)
}
