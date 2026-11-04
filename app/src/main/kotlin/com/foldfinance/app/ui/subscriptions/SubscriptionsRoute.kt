package com.foldfinance.app.ui.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * «Подписки» — entry point of the section: candidates from the detector («Похоже на подписку,
 * подтвердить?»), confirmed subscriptions with the monthly total per currency, and the calendar of
 * upcoming charges.
 */
@Composable
fun SubscriptionsRoute(modifier: Modifier = Modifier) {
    val viewModel: SubscriptionsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        SubscriptionsActions(
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismiss,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onSelectDate = viewModel::selectDate,
            onToggleCalendar = viewModel::toggleCalendar,
        )
    }
    SubscriptionsScreen(state = state, actions = actions, modifier = modifier)
}
