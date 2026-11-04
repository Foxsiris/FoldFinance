package com.foldfinance.app.ui.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.TabularNumbers
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight

/**
 * Our own digit keyboard for amounts: 3 × 4 keys, each at least [keyHeight] tall and a third of
 * the width — far above the 48 dp minimum, usable with the thumb on the outer screen. Input goes
 * to [AmountEntry] as keys, never through a float.
 */
@Composable
fun NumericKeypad(
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 56.dp,
    showDecimal: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val press: (KeypadKey) -> Unit = { key ->
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        onKey(key)
    }
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    KeypadButton(
                        label = digit.toString(),
                        onClick = { press(KeypadKey.Digit(digit)) },
                        modifier = Modifier.weight(1f).height(keyHeight),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showDecimal) {
                KeypadButton(
                    label = MoneyFormat.DECIMAL_SEPARATOR.toString(),
                    description = stringResource(R.string.tx_keypad_decimal),
                    onClick = { press(KeypadKey.Decimal) },
                    modifier = Modifier.weight(1f).height(keyHeight),
                )
            } else {
                Box(Modifier.weight(1f).height(keyHeight))
            }
            KeypadButton(
                label = "0",
                onClick = { press(KeypadKey.Digit(0)) },
                modifier = Modifier.weight(1f).height(keyHeight),
            )
            KeypadButton(
                label = null,
                description = stringResource(R.string.tx_keypad_backspace),
                onClick = { press(KeypadKey.Backspace) },
                modifier = Modifier.weight(1f).height(keyHeight),
            )
        }
    }
}

@Composable
private fun KeypadButton(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val palette = FoldFinanceTheme.palette
    Box(
        modifier = modifier
            .clip(FoldShapes.tile)
            .background(if (label == null) palette.ink.copy(alpha = 0f) else palette.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TabularNumbers),
                color = palette.text,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
