package com.foldfinance.app.ui.categories

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R

/**
 * Управление категориями: добавить, переименовать, иконка и цвет (этап 6). Экран настроек
 * открывает его по пункту «Категории»; [onBack] возвращает в настройки.
 */
@Composable
fun CategoriesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: CategoriesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.categories_saved)
    val failed = stringResource(R.string.categories_save_failed)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    CategoriesEvent.Saved -> saved
                    CategoriesEvent.SaveFailed -> failed
                },
            )
        }
    }
    val actions = remember(viewModel, onBack) {
        CategoriesActions(
            onBack = onBack,
            onAdd = viewModel::startNew,
            onEdit = viewModel::edit,
            onName = viewModel::updateName,
            onIcon = viewModel::updateIcon,
            onColor = viewModel::updateColor,
            onSave = viewModel::save,
            onCancel = viewModel::cancel,
        )
    }
    CategoriesScreen(state = state, actions = actions, snackbarHostState = snackbar, modifier = modifier)
}
