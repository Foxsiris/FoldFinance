package com.foldfinance.app.ui.diagnostics

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.SectionHeader
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.FoldReading
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.LocalWindowReadings
import com.foldfinance.app.ui.layout.SplitSource
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.layout.WindowReadings
import com.foldfinance.app.ui.layout.heightLabel
import com.foldfinance.app.ui.layout.widthLabel
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.TabularNumbers
import kotlinx.coroutines.launch

/**
 * Debug-only «Диагностика окна». Reads live [LocalWindowReadings] provided by `MainActivity`
 * (WindowManager flow), so the numbers change as the phone is folded or the window resized.
 * When no readings are provided (outside the activity) it derives them from [LocalAppLayout].
 */
@Composable
fun WindowDiagnosticsRoute(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val layout = LocalAppLayout.current
    val density = LocalDensity.current.density
    val readings = LocalWindowReadings.current
        ?: WindowReadings.fromDp(layout.windowWidthDp, layout.windowHeightDp, density, emptyList())
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copiedFor by remember { mutableStateOf<WindowReadings?>(null) }
    WindowDiagnosticsScreen(
        readings = readings,
        layout = layout,
        copied = copiedFor == readings,
        onCopy = {
            val text = diagnosticsReport(readings, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.SDK_INT)
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("FoldFinance diagnostics", text)))
                copiedFor = readings
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless diagnostics screen; [layout] arranges it (one column, book or tabletop) like any
 * other screen, so it also demonstrates [TwoPane] on the device.
 */
@Composable
fun WindowDiagnosticsScreen(
    readings: WindowReadings,
    layout: AppLayout,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val computed = readings.toAppLayout()
    when (layout) {
        is AppLayout.Compact -> PaneColumn(modifier.fillMaxSize(), start = 20.dp, end = 20.dp) {
            Header(onBack)
            WindowHero(readings)
            WindowGroup(readings)
            LayoutGroup(computed)
            FoldsGroup(readings)
            CopyButton(copied, onCopy)
        }
        is AppLayout.Book -> TwoPane(
            modifier = modifier.fillMaxSize(),
            layout = layout,
            first = {
                PaneColumn(Modifier.fillMaxSize(), start = 24.dp, end = 12.dp) {
                    Header(onBack)
                    WindowHero(readings)
                    WindowGroup(readings)
                    LayoutGroup(computed)
                }
            },
            second = {
                PaneColumn(Modifier.fillMaxSize(), start = 12.dp, end = 24.dp) {
                    FoldsGroup(readings)
                    CopyButton(copied, onCopy)
                }
            },
        )
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier.fillMaxSize(),
            layout = layout,
            first = {
                PaneColumn(Modifier.fillMaxSize(), start = 24.dp, end = 24.dp) {
                    Header(onBack)
                    WindowHero(readings)
                    LayoutGroup(computed)
                }
            },
            second = {
                PaneColumn(Modifier.fillMaxSize(), start = 24.dp, end = 24.dp) {
                    CopyButton(copied, onCopy)
                    WindowGroup(readings)
                    FoldsGroup(readings)
                }
            },
        )
    }
}

@Composable
private fun PaneColumn(
    modifier: Modifier,
    start: androidx.compose.ui.unit.Dp,
    end: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = start, end = end, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun Header(onBack: (() -> Unit)?) {
    val palette = FoldFinanceTheme.palette
    Column {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.diagnostics_back),
                    tint = palette.text,
                )
            }
        }
        Text(
            text = stringResource(R.string.diagnostics_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = palette.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.diagnostics_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textMuted,
        )
    }
}

/** The window size is the headline of this screen. */
@Composable
private fun WindowHero(readings: WindowReadings) {
    val palette = FoldFinanceTheme.palette
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${DiagnosticsFormat.dp(readings.widthDp)} × ${DiagnosticsFormat.dp(readings.heightDp)}",
                style = FoldFinanceTheme.money.displayMedium,
                color = palette.text,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "dp",
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.titleLarge,
                color = palette.textMuted,
            )
        }
        Text(
            text = stringResource(R.string.diagnostics_window_px, readings.widthPx, readings.heightPx),
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TabularNumbers),
            color = palette.textMuted,
        )
    }
}

@Composable
private fun WindowGroup(readings: WindowReadings) {
    val sizeClass = readings.sizeClass
    Group(stringResource(R.string.diagnostics_window)) {
        ValueRow(stringResource(R.string.diagnostics_density), DiagnosticsFormat.density(readings.density))
        ListGroupDivider(startInset = 16.dp)
        ValueRow(stringResource(R.string.diagnostics_size_class_width), sizeClass.widthLabel())
        ListGroupDivider(startInset = 16.dp)
        ValueRow(stringResource(R.string.diagnostics_size_class_height), sizeClass.heightLabel())
    }
}

@Composable
private fun LayoutGroup(layout: AppLayout) {
    Group(stringResource(R.string.diagnostics_layout)) {
        val mode = when (layout) {
            is AppLayout.Compact -> R.string.diagnostics_layout_compact
            is AppLayout.Book -> R.string.diagnostics_layout_book
            is AppLayout.Tabletop -> R.string.diagnostics_layout_tabletop
        }
        ValueRow(stringResource(R.string.diagnostics_layout_mode), stringResource(mode))
        when (layout) {
            is AppLayout.Compact -> Unit
            is AppLayout.Book -> {
                val source = if (layout.splitSource == SplitSource.Hinge) {
                    R.string.diagnostics_layout_split_hinge
                } else {
                    R.string.diagnostics_layout_split_proportion
                }
                ListGroupDivider(startInset = 16.dp)
                ValueRow(
                    stringResource(R.string.diagnostics_layout_split),
                    "x = ${DiagnosticsFormat.dp(layout.splitAtDp)} dp, ${stringResource(source)}",
                )
                ListGroupDivider(startInset = 16.dp)
                ValueRow(stringResource(R.string.diagnostics_layout_hinge_size), "${DiagnosticsFormat.dp(layout.hingeWidthDp)} dp")
            }
            is AppLayout.Tabletop -> {
                ListGroupDivider(startInset = 16.dp)
                ValueRow(
                    stringResource(R.string.diagnostics_layout_split),
                    "y = ${DiagnosticsFormat.dp(layout.splitAtDp)} dp, ${stringResource(R.string.diagnostics_layout_split_hinge)}",
                )
                ListGroupDivider(startInset = 16.dp)
                ValueRow(stringResource(R.string.diagnostics_layout_hinge_size), "${DiagnosticsFormat.dp(layout.hingeHeightDp)} dp")
            }
        }
    }
}

@Composable
private fun FoldsGroup(readings: WindowReadings) {
    val palette = FoldFinanceTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.diagnostics_folds))
        if (readings.folds.isEmpty()) {
            ListGroup {
                Text(
                    text = stringResource(R.string.diagnostics_folds_none),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textMuted,
                )
            }
        }
        readings.folds.forEachIndexed { index, fold ->
            if (readings.folds.size > 1) {
                Text(
                    text = stringResource(R.string.diagnostics_fold_title, index + 1),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                )
            }
            FoldCard(fold, readings.density)
        }
    }
}

@Composable
private fun FoldCard(fold: FoldReading, density: Float) {
    ListGroup {
        ValueRow(stringResource(R.string.diagnostics_fold_bounds_px), DiagnosticsFormat.px(fold.boundsPx))
        ListGroupDivider(startInset = 16.dp)
        ValueRow(stringResource(R.string.diagnostics_fold_bounds_dp), DiagnosticsFormat.dp(fold.boundsPx, density))
        ListGroupDivider(startInset = 16.dp)
        ValueRow(stringResource(R.string.diagnostics_fold_state), fold.state.name, highlight = true)
        ListGroupDivider(startInset = 16.dp)
        ValueRow(stringResource(R.string.diagnostics_fold_orientation), fold.orientation.name)
        ListGroupDivider(startInset = 16.dp)
        ValueRow(stringResource(R.string.diagnostics_fold_separating), fold.isSeparating.toString())
        ListGroupDivider(startInset = 16.dp)
        ValueRow(stringResource(R.string.diagnostics_fold_occlusion), fold.occlusion.name)
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        ListGroup(content = content)
    }
}

@Composable
private fun ValueRow(label: String, value: String, highlight: Boolean = false) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textMuted,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TabularNumbers),
            color = if (highlight) palette.saffron else palette.text,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CopyButton(copied: Boolean, onCopy: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Button(
        onClick = onCopy,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = FoldShapes.key,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
    ) {
        Icon(
            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = stringResource(if (copied) R.string.diagnostics_copied else R.string.diagnostics_copy),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
