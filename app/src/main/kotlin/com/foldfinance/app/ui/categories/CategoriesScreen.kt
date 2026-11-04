package com.foldfinance.app.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryIcons
import com.foldfinance.app.ui.components.CategoryTile
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.categoryPaletteArgb
import com.foldfinance.app.ui.components.categoryPaletteSlotOf
import com.foldfinance.app.ui.components.categoryVisual
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.layout.TwoPaneDefaults
import com.foldfinance.app.ui.theme.CategoryPalette
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.core.model.Category

/** User actions of the categories screen. */
@Immutable
data class CategoriesActions(
    val onBack: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (Category) -> Unit = {},
    val onName: (String) -> Unit = {},
    val onIcon: (String) -> Unit = {},
    val onColor: (Int?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * Stateless categories screen. Book and tabletop: the list and the editor side by side / one over
 * the other, split on the hinge; compact: the list, and the editor over it («Назад» returns).
 */
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    actions: CategoriesActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    Box(modifier.fillMaxSize()) {
        when (layout) {
            is AppLayout.Compact -> {
                val draft = state.draft
                if (draft != null) {
                    BackHandler(onBack = actions.onCancel)
                    EditorPane(state, draft, actions, start = 20.dp, end = 20.dp, showBack = true)
                } else {
                    ListPane(state, actions, start = 20.dp, end = 20.dp)
                }
            }
            is AppLayout.Book, is AppLayout.Tabletop -> {
                if (state.draft != null) BackHandler(onBack = actions.onCancel)
                val book = layout is AppLayout.Book
                TwoPane(
                    modifier = Modifier.fillMaxSize(),
                    first = { ListPane(state, actions, start = 24.dp, end = if (book) TwoPaneDefaults.SeamClearance else 24.dp) },
                    second = {
                        val draft = state.draft
                        val start = if (book) TwoPaneDefaults.SeamClearance else 24.dp
                        if (draft != null) {
                            EditorPane(state, draft, actions, start = start, end = 24.dp, showBack = false)
                        } else {
                            Box(Modifier.fillMaxSize().padding(start = start, end = 24.dp, top = 24.dp)) {
                                Text(
                                    stringResource(R.string.categories_edit_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = FoldFinanceTheme.palette.textMuted,
                                )
                            }
                        }
                    },
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

// ---------------------------------------------------------------- List

@Composable
private fun ListPane(state: CategoriesUiState, actions: CategoriesActions, start: Dp, end: Dp) {
    val palette = FoldFinanceTheme.palette
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = start, end = end, top = 12.dp, bottom = 16.dp),
        ) {
            TitleRow(stringResource(R.string.categories_title), onBack = actions.onBack)
            Spacer(Modifier.height(16.dp))
            if (state.categories.isNotEmpty()) {
                ListGroup(Modifier.fillMaxWidth()) {
                    state.categories.forEachIndexed { index, category ->
                        if (index > 0) ListGroupDivider()
                        CategoryRow(category, selected = state.draft?.id == category.id, onClick = { actions.onEdit(category) })
                    }
                }
            }
        }
        // Thumb zone: «Добавить» at the bottom of the pane.
        Button(
            onClick = actions.onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = start, end = end, bottom = 16.dp, top = 4.dp)
                .heightIn(min = 52.dp),
            shape = FoldShapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = palette.saffron, contentColor = palette.onSaffron),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.categories_add), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TitleRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.categories_back),
                tint = FoldFinanceTheme.palette.text,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = FoldFinanceTheme.palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryRow(category: Category, selected: Boolean, onClick: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(palette.saffronContainer.copy(alpha = 0.55f)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category.visual())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (category.isSystem) R.string.categories_base else R.string.categories_own),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
    }
}

// ---------------------------------------------------------------- Editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorPane(state: CategoriesUiState, draft: CategoryDraft, actions: CategoriesActions, start: Dp, end: Dp, showBack: Boolean) {
    val palette = FoldFinanceTheme.palette
    val key = state.editing?.key ?: "user_new"
    val preview = categoryVisual(key, draft.name.ifBlank { " " }, draft.iconKey, draft.colorArgb)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = start, end = end, top = 12.dp, bottom = 16.dp),
        ) {
            val title = stringResource(if (draft.id == null) R.string.categories_editor_new else R.string.categories_editor_edit)
            if (showBack) {
                TitleRow(title, onBack = actions.onCancel)
            } else {
                Text(
                    title,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.text,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(preview)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = actions.onName,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.categories_name)) },
                    singleLine = true,
                    isError = draft.nameError,
                    supportingText = if (draft.nameError) {
                        { Text(stringResource(R.string.categories_name_error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    shape = FoldShapes.tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.saffron,
                        focusedLabelColor = palette.saffron,
                        cursorColor = palette.saffron,
                    ),
                )
            }
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.categories_icon))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryIcons.keys.forEach { iconKey ->
                    IconChoice(iconKey, selected = iconKey == draft.iconKey, tint = preview.color, onClick = { actions.onIcon(iconKey) })
                }
            }
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.categories_color))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val autoColor = palette.categories.forKey(key)
                ColorChoice(
                    color = autoColor,
                    selected = draft.colorArgb == null,
                    label = stringResource(R.string.categories_color_auto),
                    onClick = { actions.onColor(null) },
                    auto = true,
                )
                val selectedSlot = draft.colorArgb?.let(::categoryPaletteSlotOf) ?: -1
                for (slot in 0 until CategoryPalette.SIZE) {
                    ColorChoice(
                        color = palette.categories[slot],
                        selected = selectedSlot == slot,
                        label = stringResource(R.string.categories_color_slot, slot + 1),
                        onClick = { actions.onColor(categoryPaletteArgb(slot)) },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = start, end = end, bottom = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(stringResource(R.string.categories_cancel), style = MaterialTheme.typography.labelLarge, color = palette.text)
            }
            Button(
                onClick = actions.onSave,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = FoldShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = palette.saffron, contentColor = palette.onSaffron),
            ) { Text(stringResource(R.string.categories_save), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun ColumnScope.SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = FoldFinanceTheme.palette.text)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun IconChoice(iconKey: String, selected: Boolean, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Box(
        Modifier
            .size(48.dp)
            .clip(FoldShapes.tile)
            .background(if (selected) tint.copy(alpha = if (palette.isDark) 0.22f else 0.16f) else palette.surfaceRaised)
            .then(if (selected) Modifier.border(BorderStroke(1.5.dp, tint), FoldShapes.tile) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = iconKey.replace('_', ' ') },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIcons.forKey(iconKey),
            contentDescription = null,
            tint = if (selected) tint else palette.textMuted,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ColorChoice(
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    auto: Boolean = false,
) {
    val palette = FoldFinanceTheme.palette
    Box(
        Modifier
            .size(48.dp)
            .clip(FoldShapes.pill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 40.dp else 34.dp)
                .background(color, FoldShapes.pill)
                .then(if (selected) Modifier.border(BorderStroke(3.dp, palette.surface), FoldShapes.pill) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selected -> Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.surface, modifier = Modifier.size(18.dp))
                auto -> Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = palette.surface, modifier = Modifier.size(16.dp))
            }
        }
    }
}
