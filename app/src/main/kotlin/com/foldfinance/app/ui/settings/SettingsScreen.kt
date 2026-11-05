package com.foldfinance.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DevicesFold
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.prefs.RelockTimeout
import com.foldfinance.app.prefs.ThemeMode
import com.foldfinance.app.security.DeviceAuthStatus
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/** Действия экрана настроек. Реализация по умолчанию ничего не делает (скриншоты, превью). */
interface SettingsActions {
    fun selectPane(pane: SettingsPane?) {}
    fun setAutoTracking(enabled: Boolean) {}
    fun setOwnCard(mask: String, own: Boolean) {}
    fun setCardLabel(mask: String, label: String) {}
    fun setDefaultCurrency(currency: String) {}
    fun setLockEnabled(enabled: Boolean) {}
    fun setRelockTimeout(timeout: RelockTimeout) {}
    fun setSecureWindow(enabled: Boolean) {}
    fun setHideWidgetAmounts(hide: Boolean) {}
    fun setThemeMode(mode: ThemeMode) {}
    fun setDynamicColor(enabled: Boolean) {}
    fun startExport() {}
    fun startImport() {}
    fun openSecuritySettings() {}
    fun dismissPassword() {}

    /** [password] принадлежит получателю: он обязан затереть массив. */
    fun submitPassword(password: CharArray) {
        password.fill('\u0000')
    }

    companion object {
        val None: SettingsActions = object : SettingsActions {}
    }
}

/**
 * Экран настроек без состояния.
 *
 * - Внешний экран: список разделов; выбранный раздел открывается поверх, «Назад» — к списку.
 * - Книжная поза / tabletop: [TwoPane] по шарниру — список слева (сверху), раздел справа (снизу).
 *   Без явного выбора справа открыт первый раздел.
 *
 * Чужие блоки встраиваются слотами: [ingestSection] — «Доступы и источники» (ingest),
 * [aiSection] — «Локальный ИИ», [categories] — управление категориями, [diagnostics] — debug-экран окна.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    selected: SettingsPane?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    panes: List<SettingsPane> = SettingsPane.visible(),
    layout: AppLayout = LocalAppLayout.current,
    ingestSection: @Composable () -> Unit = {},
    aiSection: @Composable () -> Unit = {},
    categories: @Composable (onBack: () -> Unit) -> Unit = {},
    diagnostics: @Composable (onBack: () -> Unit) -> Unit = {},
    onOpenSubscriptions: (() -> Unit)? = null,
) {
    val back = { actions.selectPane(null) }
    if (selected == SettingsPane.DIAGNOSTICS) {
        BackHandler(onBack = back)
        diagnostics(back)
        return
    }
    val passwordRequest = state.passwordRequest
    if (passwordRequest != null) {
        BackupPasswordDialog(
            kind = passwordRequest.kind,
            onDismiss = actions::dismissPassword,
            onConfirm = actions::submitPassword,
        )
    }

    if (layout is AppLayout.Compact) {
        if (selected == null) {
            SettingsList(
                state, panes, selected = null, twoPane = false, onSelect = actions::selectPane, modifier = modifier,
                // The outer-screen bar has no «Подписки»: the section is one row away here.
                onOpenSubscriptions = onOpenSubscriptions,
            )
        } else {
            BackHandler(onBack = back)
            PaneHost(state, selected, actions, ingestSection, aiSection, categories, onBack = back, modifier = modifier)
        }
    } else {
        val shown = selected ?: panes.first()
        TwoPane(
            modifier = modifier.fillMaxSize(),
            layout = layout,
            first = {
                if (layout is AppLayout.Tabletop) {
                    // Верхняя половина невысокая: разделы — пилюлями, выбранный всегда на виду.
                    SettingsPills(panes, selected = shown, onSelect = actions::selectPane)
                } else {
                    SettingsList(state, panes, selected = shown, twoPane = true, onSelect = actions::selectPane)
                }
            },
            second = { PaneHost(state, shown, actions, ingestSection, aiSection, categories, onBack = null) },
        )
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState,
    panes: List<SettingsPane>,
    selected: SettingsPane?,
    twoPane: Boolean,
    onSelect: (SettingsPane) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSubscriptions: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Узкая левая панель (≈ 230 dp у шарнира на 674 dp): значок и одна строка, без описаний.
        val compactRows = twoPane && maxWidth < CompactListWidth
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (compactRows) 12.dp else 24.dp,
                    end = if (compactRows) 0.dp else 24.dp,
                    top = 24.dp,
                    bottom = 24.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                modifier = Modifier.padding(start = if (compactRows) 8.dp else 0.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = FoldFinanceTheme.palette.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(if (compactRows) 16.dp else 20.dp))
            ListGroup(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                panes.forEachIndexed { index, pane ->
                    if (index > 0) ListGroupDivider(startInset = if (compactRows) 54.dp else 68.dp)
                    if (compactRows) {
                        SettingsCompactNavRow(
                            icon = pane.icon(),
                            title = stringResource(pane.titleRes()),
                            selected = pane == selected,
                            onClick = { onSelect(pane) },
                        )
                    } else {
                        SettingsNavRow(
                            icon = pane.icon(),
                            title = stringResource(pane.titleRes()),
                            summary = paneSummary(pane, state),
                            selected = pane == selected,
                            // В двух панелях раздел открывается рядом — стрелка «вперёд» не нужна.
                            showChevron = !twoPane || pane == SettingsPane.DIAGNOSTICS,
                            onClick = { onSelect(pane) },
                        )
                    }
                }
            }
            if (onOpenSubscriptions != null) {
                Spacer(Modifier.height(16.dp))
                ListGroup(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    SettingsNavRow(
                        icon = Icons.Outlined.Autorenew,
                        title = stringResource(R.string.settings_subscriptions),
                        summary = stringResource(R.string.settings_subscriptions_summary),
                        selected = false,
                        showChevron = true,
                        onClick = onOpenSubscriptions,
                    )
                }
            }
        }
    }
}

/** Narrower than this, the two-pane list of sections shows compact rows. */
private val CompactListWidth = 300.dp

/** Содержимое раздела с заголовком; на внешнем экране — со стрелкой «Назад». */
@Composable
private fun PaneHost(
    state: SettingsUiState,
    pane: SettingsPane,
    actions: SettingsActions,
    ingestSection: @Composable () -> Unit,
    aiSection: @Composable () -> Unit,
    categories: @Composable (onBack: () -> Unit) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (pane == SettingsPane.CATEGORIES) {
        // Экран категорий со своим заголовком; в двух панелях «Назад» возвращает к первому разделу.
        Box(modifier.fillMaxSize()) { categories { actions.selectPane(null) } }
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = if (onBack != null) 8.dp else 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
    ) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        tint = FoldFinanceTheme.palette.text,
                    )
                }
            }
            Text(
                text = stringResource(pane.titleRes()),
                modifier = Modifier
                    .padding(start = if (onBack != null) 4.dp else 0.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = FoldFinanceTheme.palette.text,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .padding(start = if (onBack != null) 16.dp else 0.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (pane) {
                SettingsPane.SOURCES -> SourcesPane(state, actions, ingestSection)
                SettingsPane.CARDS -> CardsPane(state, actions)
                SettingsPane.CURRENCY -> CurrencyPane(state, actions)
                SettingsPane.SECURITY -> SecurityPane(state, actions)
                SettingsPane.BACKUP -> BackupPane(state, actions)
                SettingsPane.APPEARANCE -> AppearancePane(state, actions)
                SettingsPane.AI -> aiSection()
                SettingsPane.CATEGORIES, SettingsPane.DIAGNOSTICS -> Unit
            }
        }
    }
}

internal fun SettingsPane.titleRes(): Int = when (this) {
    SettingsPane.SOURCES -> R.string.settings_pane_sources
    SettingsPane.CARDS -> R.string.settings_pane_cards
    SettingsPane.CURRENCY -> R.string.settings_pane_currency
    SettingsPane.CATEGORIES -> R.string.settings_pane_categories
    SettingsPane.AI -> R.string.ai_settings_title
    SettingsPane.SECURITY -> R.string.settings_pane_security
    SettingsPane.BACKUP -> R.string.settings_pane_backup
    SettingsPane.APPEARANCE -> R.string.settings_pane_appearance
    SettingsPane.DIAGNOSTICS -> R.string.settings_pane_diagnostics
}

private fun SettingsPane.icon(): ImageVector = when (this) {
    SettingsPane.SOURCES -> Icons.Outlined.Sms
    SettingsPane.CARDS -> Icons.Outlined.CreditCard
    SettingsPane.CURRENCY -> Icons.Outlined.CurrencyExchange
    SettingsPane.CATEGORIES -> Icons.Outlined.Category
    SettingsPane.AI -> Icons.Outlined.AutoAwesome
    SettingsPane.SECURITY -> Icons.Outlined.Lock
    SettingsPane.BACKUP -> Icons.Outlined.Backup
    SettingsPane.APPEARANCE -> Icons.Outlined.Palette
    SettingsPane.DIAGNOSTICS -> Icons.Outlined.DevicesFold
}

@Composable
private fun paneSummary(pane: SettingsPane, state: SettingsUiState): String? = when (pane) {
    SettingsPane.SOURCES -> stringResource(
        if (state.autoTracking) R.string.settings_summary_tracking_on else R.string.settings_summary_tracking_off,
    )
    SettingsPane.CARDS -> if (state.cards.isEmpty()) {
        stringResource(R.string.settings_summary_cards_none)
    } else {
        stringResource(R.string.settings_summary_cards, state.cards.count { it.isOwn }, state.cards.size)
    }
    SettingsPane.CURRENCY -> currencyName(state.settings.defaultCurrency)
    SettingsPane.CATEGORIES -> stringResource(R.string.settings_summary_categories)
    SettingsPane.AI -> stringResource(R.string.ai_settings_summary)
    SettingsPane.SECURITY -> when {
        !state.settings.lockEnabled -> stringResource(R.string.settings_summary_lock_off)
        state.deviceAuth != DeviceAuthStatus.AVAILABLE -> stringResource(R.string.settings_summary_lock_unprotected)
        else -> stringResource(R.string.settings_summary_lock_on, stringResource(state.settings.relockTimeout.shortRes()))
    }
    SettingsPane.BACKUP -> stringResource(R.string.settings_summary_backup)
    SettingsPane.APPEARANCE -> stringResource(state.settings.themeMode.titleRes())
    SettingsPane.DIAGNOSTICS -> stringResource(R.string.diagnostics_entry_body)
}

/** Разделы пилюлями (tabletop: верхняя половина над шарниром). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPills(panes: List<SettingsPane>, selected: SettingsPane, onSelect: (SettingsPane) -> Unit) {
    val palette = FoldFinanceTheme.palette
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = palette.text,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            panes.forEach { pane ->
                val isSelected = pane == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(pane) },
                    label = { Text(stringResource(pane.titleRes()), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { Icon(pane.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = FoldShapes.pill,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = palette.surface,
                        labelColor = palette.text,
                        iconColor = palette.saffron,
                        selectedContainerColor = palette.saffronContainer,
                        selectedLabelColor = palette.onSaffronContainer,
                        selectedLeadingIconColor = palette.onSaffronContainer,
                    ),
                    border = null,
                )
            }
        }
    }
}
