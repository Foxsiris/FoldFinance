package com.foldfinance.app.ui.layout

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.HingePolicy
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * [PaneScaffoldDirective] for `ListDetailPaneScaffold` / `SupportingPaneScaffold` that agrees
 * with [AppLayout]:
 *
 * - two horizontal partitions from Medium width
 *   (`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`, adaptive-layout 1.3.0; still
 *   `@ExperimentalMaterial3AdaptiveApi`, the opt-in is contained here);
 * - `HingePolicy.AlwaysAvoid`, so a FLAT, non-separating Fold hinge is still excluded and the
 *   scaffold puts the pane boundary on it (the default `AvoidSeparating` would ignore it);
 * - one partition when [layout] is [AppLayout.Compact]; in [AppLayout.Tabletop] Material's own
 *   directive is kept (screens usually stack top/bottom content themselves with [TwoPane]);
 * - the hinge from [layout] added to `excludedBounds` — this is what makes the scaffold follow
 *   the hinge in screenshot tests and previews, where WindowManager reports no folds.
 *
 * Usage: `ListDetailPaneScaffold(directive = navigator.scaffoldDirective, …)` with
 * `rememberListDetailPaneScaffoldNavigator(scaffoldDirective = foldPaneScaffoldDirective())`.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun foldPaneScaffoldDirective(
    layout: AppLayout = LocalAppLayout.current,
    adaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
): PaneScaffoldDirective {
    val base = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(adaptiveInfo, HingePolicy.AlwaysAvoid)
    val density = LocalDensity.current
    return when (layout) {
        is AppLayout.Book -> {
            val hinge = if (layout.splitSource == SplitSource.Hinge) {
                with(density) {
                    val half = layout.hingeWidthDp.dp.toPx() / 2f
                    val x = layout.splitAtDp.dp.toPx()
                    Rect(x - half, 0f, x + half, layout.windowHeightDp.dp.toPx())
                }
            } else {
                null
            }
            base.copy(
                maxHorizontalPartitions = 2,
                excludedBounds = (base.excludedBounds + listOfNotNull(hinge)).distinct(),
            )
        }
        is AppLayout.Tabletop -> base
        is AppLayout.Compact -> base.copy(maxHorizontalPartitions = 1)
    }
}
