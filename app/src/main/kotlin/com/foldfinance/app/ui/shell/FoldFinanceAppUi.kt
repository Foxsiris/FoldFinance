package com.foldfinance.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemColors
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.onboarding.OnboardingRoute
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.PaletteTokens
import com.foldfinance.app.ui.transaction.LocalAppSnackbar

/**
 * Root UI of the app: adaptive navigation, the selected section, the «на проверку» badge,
 * the shared snackbar and — on first run — the onboarding over everything.
 *
 * Reads the window layout from [LocalAppLayout] (provided by `MainActivity`, or explicitly by
 * tests/previews) and all state from [ShellViewModel], so nothing is lost on folding, rotation
 * and activity recreation.
 */
@Composable
fun FoldFinanceAppUi(
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = hiltViewModel(),
    content: @Composable (Section, Modifier) -> Unit = { section, m -> SectionContent(section, m) },
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val reviewCount by viewModel.reviewCount.collectAsStateWithLifecycle()
    val onboarding by viewModel.onboarding.collectAsStateWithLifecycle()
    val pending by viewModel.pendingTransaction.collectAsStateWithLifecycle()
    val sections = viewModel.visibleSections
    val navigation = remember(viewModel, pending) {
        object : ShellNavigation {
            override val pendingTransactionId: Long? = pending
            override fun openSection(section: Section) = viewModel.select(section)
            override fun openTransaction(transactionId: Long) = viewModel.openTransaction(transactionId)
            override fun consumePendingTransaction() = viewModel.consumePendingTransaction()
        }
    }

    // Back from any other section returns to the first one before leaving the app. Registered
    // before the sections' own handlers, so a detail or an open editor closes first.
    BackHandler(enabled = selected != sections.first()) { viewModel.select(sections.first()) }

    CompositionLocalProvider(LocalShellNavigation provides navigation) {
        Box(modifier.fillMaxSize()) {
            if (onboarding != OnboardingState.Unknown) {
                ShellScaffold(
                    sections = sections,
                    selected = selected,
                    onSelect = viewModel::select,
                    badges = mapOf(Section.Review to reviewCount),
                    content = content,
                )
            }
            if (onboarding == OnboardingState.Required) {
                OnboardingRoute(onFinished = viewModel::finishOnboarding, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Navigation type per window layout: bar in the thumb zone, rail on the inner screen. */
fun navigationTypeFor(layout: AppLayout, sectionCount: Int): NavigationSuiteType = when {
    // A single section needs no navigation at all.
    sectionCount <= 1 -> NavigationSuiteType.None
    // Book posture: the rail keeps the panes wide and stays clear of the vertical hinge.
    layout is AppLayout.Book -> NavigationSuiteType.WideNavigationRailCollapsed
    // Compact, and tabletop: navigation is a «touch» thing, it belongs to the bottom half.
    else -> NavigationSuiteType.ShortNavigationBarCompact
}

/**
 * Stateless shell: `NavigationSuiteScaffold` (Material 3 1.4 short navigation bar / wide
 * navigation rail) around the content of [selected]. Each section keeps its saveable state
 * (scroll, filters, the list-detail navigator) while another one is shown.
 *
 * On a Compact window the bar shows at most five sections ([barSections]); a selected section
 * that has no bar item ([SectionsOutsideCompactBar]) is still shown, with nothing highlighted.
 *
 * Insets: the scaffold consumes the insets of the side its navigation occupies; the content pads
 * the remaining safe-drawing insets (status bar, camera cutout, Samsung taskbar).
 *
 * @param badges counts shown on navigation items (only values > 0 are drawn).
 * @param snackbar shared snackbar host, provided to screens as [LocalAppSnackbar].
 */
@Composable
fun ShellScaffold(
    sections: List<Section>,
    selected: Section,
    onSelect: (Section) -> Unit,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
    badges: Map<Section, Int> = emptyMap(),
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (Section, Modifier) -> Unit = { section, m -> SectionContent(section, m) },
) {
    val palette = FoldFinanceTheme.palette
    val compactBar = layout is AppLayout.Compact
    val items = barSections(sections, compactBar)
    val type = navigationTypeFor(layout, items.size)
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
            items.forEach { section ->
                val isSelected = section == selected
                val badge = badges[section] ?: 0
                NavigationSuiteItem(
                    selected = isSelected,
                    onClick = { onSelect(section) },
                    icon = { NavigationIcon(section, isSelected, badge) },
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
        CompositionLocalProvider(LocalAppSnackbar provides snackbar) {
            Box(Modifier.fillMaxSize()) {
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
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        // Above the thumb-zone action row of the outer screen.
                        .padding(bottom = if (compactBar) 84.dp else 16.dp),
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = FoldShapes.tile,
                        containerColor = palette.text,
                        contentColor = palette.ink,
                        actionColor = if (palette.isDark) PaletteTokens.Light.saffron else PaletteTokens.Dark.saffron,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(section: Section, selected: Boolean, badge: Int) {
    val palette = FoldFinanceTheme.palette
    val icon = if (selected) section.selectedIcon else section.icon
    if (badge > 0) {
        val badgeDescription = pluralStringResource(R.plurals.shell_badge_review, badge, badge)
        BadgedBox(
            badge = {
                Badge(
                    containerColor = palette.saffron,
                    contentColor = palette.onSaffron,
                    modifier = Modifier.semantics { contentDescription = badgeDescription },
                ) { Text(if (badge > 99) "99+" else badge.toString()) }
            },
        ) { Icon(imageVector = icon, contentDescription = null) }
    } else {
        Icon(imageVector = icon, contentDescription = null)
    }
}
