package com.foldfinance.app.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * «Аналитика» — entry point of the section: periods, totals per currency, dynamics chart,
 * comparison with the previous period and the category breakdown, all from the database.
 */
@Composable
fun AnalyticsRoute(modifier: Modifier = Modifier) {
    val viewModel: AnalyticsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        AnalyticsActions(
            onSelectType = viewModel::selectType,
            onSelectCustom = viewModel::selectCustom,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onSelectBucket = viewModel::selectBucket,
            onClearSelection = viewModel::clearSelection,
            onSelectCurrency = viewModel::selectCurrency,
        )
    }
    AnalyticsScreen(state = state, actions = actions, modifier = modifier)
}
