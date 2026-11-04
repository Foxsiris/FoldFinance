package com.foldfinance.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/** 40 dp плитка значка раздела (без описания для TalkBack: рядом есть название). */
@Composable
internal fun SettingsIcon(icon: ImageVector, tint: Color = FoldFinanceTheme.palette.saffron) {
    val alpha = if (FoldFinanceTheme.palette.isDark) 0.18f else 0.14f
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(tint.copy(alpha = alpha), FoldShapes.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Строка списка разделов: значок, название, текущее значение; выбранная — шафрановая подложка. */
@Composable
internal fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    selected: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) palette.saffronContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) palette.onSaffronContainer else palette.text)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) palette.onSaffronContainer.copy(alpha = 0.8f) else palette.textMuted,
                )
            }
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textMuted)
        }
    }
}

/** Переключатель на всю строку: тап по тексту тоже переключает. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
    enabled: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onSaffron,
                checkedTrackColor = palette.saffron,
                uncheckedThumbColor = palette.textMuted,
                uncheckedTrackColor = palette.surfaceRaised,
                uncheckedBorderColor = palette.outline,
            ),
        )
    }
}

/** Вариант выбора (радиокнопка) на всю строку. */
@Composable
internal fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = palette.saffron, unselectedColor = palette.textMuted),
        )
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = palette.text)
        if (supporting != null) {
            Spacer(Modifier.width(12.dp))
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        }
    }
}

/** Действие с пояснением (создать копию, восстановить). */
@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        }
    }
}

/** Подзаголовок группы внутри раздела. */
@Composable
internal fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = FoldFinanceTheme.palette.text,
    )
}

/** Пояснение к разделу. */
@Composable
internal fun SettingsIntro(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = FoldFinanceTheme.palette.textMuted,
    )
}

/** Предупреждение (телефон без PIN): что случилось и как исправить, с одной кнопкой. */
@Composable
internal fun SettingsWarning(text: String, actionLabel: String?, onAction: (() -> Unit)?) {
    val palette = FoldFinanceTheme.palette
    Surface(shape = FoldShapes.listGroup, color = palette.alertContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium, color = palette.text)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = palette.alert)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
