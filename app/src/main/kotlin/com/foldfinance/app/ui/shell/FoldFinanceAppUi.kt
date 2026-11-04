package com.foldfinance.app.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemColors
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion

/**
 * Root UI of the app: adaptive navigation + the selected section.
 *
 * Reads the window layout from [LocalAppLayout] (provided by `MainActivity`, or explicitly by
 * tests/previews) and the selected section from [ShellViewModel], so the selection survives
 * folding, rotation and activity recreation.
 */
@Composable
fun FoldFinanceAppUi(
    modifier: Modifier = Modifier,
    sections: List<Section> = Sections.visible(),
    viewModel: ShellViewModel = viewModel { ShellViewModel(createSavedStateHandle(), sections) },
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    ShellScaffold(
        sections = sections,
        selected = selected,
        onSelect = viewModel::select,
        modifier = modifier,
    )
}

/** Navigation type per window layout: bar in the thumb zone, rail on the inner screen. */
fun navigationTypeFor(layout: AppLayout, sectionCount: Int): NavigationSuiteType = when {
    // A single section (release before stage 5) needs no navigation at all.
    sectionCount <= 1 -> NavigationSuiteType.None
    // Book posture: the rail keeps the panes wide and stays clear of the vertical hinge.
    layout is AppLayout.Book -> NavigationSuiteType.WideNavigationRailCollapsed
    // Compact, and tabletop: navigation is a «touch» thing, it belongs to the bottom half.
    else -> NavigationSuiteType.ShortNavigationBarCompact
}

/**
 * Stateless shell: `NavigationSuiteScaffold` (Material 3 1.4 short navigation bar / wide
 * navigation rail) around the content of [selected]. Each section keeps its saveable state
 * (scroll, filters) while another one is shown.
 *
 * Insets: the scaffold consumes the insets of the side its navigation occupies; the content pads
 * the remaining safe-drawing insets (status bar, camera cutout, Samsung taskbar).
 */
@Composable
fun ShellScaffold(
    sections: List<Section>,
    selected: Section,
    onSelect: (Section) -> Unit,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
    content: @Composable (Section, Modifier) -> Unit = { section, m -> SectionContent(section, m) },
) {
    val palette = FoldFinanceTheme.palette
    val type = navigationTypeFor(layout, sections.size)
    val barItemColors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = palette.onSaffronContainer,
        selectedTextColor = palette.text,
        selectedIndicatorColor = palette.saffronContainer,
        unselectedIconColor = palette.textMuted,
        unselectedTextColor = palette.textMuted,
    )
    val railItemColors = WideNavigationRailItemDefaults.colors(
        selectedIconColor = palette.onSaffronContainer,
        selectedTextColor = palette.text,
        selectedIndicatorColor = palette.saffronContainer,
        unselectedIconColor = palette.textMuted,
        unselectedTextColor = palette.textMuted,
    )
    val itemColors: NavigationItemColors =
        if (type == NavigationSuiteType.WideNavigationRailCollapsed) railItemColors else barItemColors
    val stateHolder = rememberSaveableStateHolder()
    val reducedMotion = FoldFinanceTheme.reducedMotion

    NavigationSuiteScaffold(
        navigationItems = {
            sections.forEach { section ->
                val isSelected = section == selected
                NavigationSuiteItem(
                    selected = isSelected,
                    onClick = { onSelect(section) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) section.selectedIcon else section.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(section.labelRes), maxLines = 1) },
                    navigationSuiteType = type,
                    colors = itemColors,
                )
            }
        },
        modifier = modifier,
        navigationSuiteType = type,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            shortNavigationBarContainerColor = palette.surface,
            shortNavigationBarContentColor = palette.textMuted,
            wideNavigationRailColors = WideNavigationRailDefaults.colors(
                containerColor = palette.ink,
                contentColor = palette.textMuted,
            ),
        ),
        containerColor = palette.ink,
        contentColor = palette.text,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(FoldMotion.effects(reducedMotion)) togetherWith fadeOut(FoldMotion.effects(reducedMotion))
            },
            label = "section",
        ) { section ->
            stateHolder.SaveableStateProvider(section.name) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    content(section, Modifier.fillMaxSize())
                }
            }
        }
    }
}
