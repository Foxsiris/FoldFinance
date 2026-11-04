package com.foldfinance.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ingest.ScanState
import com.foldfinance.app.ingest.sms.ScanProgress
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.parser.Direction

/*
 * Live counters of the SMS history import, in two parts so tabletop can put the headline in the
 * top half and the counters next to the button: the scanned count is the headline (Unbounded),
 * then found / income / expense in a list group. Sums are RUB only and labelled «по известным
 * операциям» — never a bank balance.
 */

/** Scanned count (Unbounded headline), progress bar and status line. */
@Composable
fun ScanHeadline(scan: ScanState, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val progress = scan.progressOrEmpty()
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = MoneyFormat.groupThousands(progress.scanned.toString()),
            style = FoldFinanceTheme.money.displayMedium,
            color = palette.text,
        )
        Text(
            text = if (progress.total > 0) {
                stringResource(R.string.onboarding_scan_scanned, MoneyFormat.groupThousands(progress.total.toString()))
            } else {
                stringResource(R.string.onboarding_scan_scanned_unknown)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        when (scan) {
            is ScanState.Running -> {
                val fraction = progress.fraction
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.saffron,
                        trackColor = palette.surfaceRaised,
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.saffron, trackColor = palette.surfaceRaised)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.onboarding_scan_running), style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
            }
            is ScanState.Finished -> StatusLine(ok = true, text = stringResource(R.string.onboarding_scan_finished))
            ScanState.Failed -> Text(
                stringResource(R.string.onboarding_scan_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.alert,
            )
            ScanState.Idle -> Unit
        }
    }
}

/** Found / income / expense in a list group; RUB sums only, «по известным операциям». */
@Composable
fun ScanCounters(scan: ScanState, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val progress = scan.progressOrEmpty()
    Column(modifier) {
        ListGroup(Modifier.fillMaxWidth()) {
            IngestRow(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = stringResource(R.string.onboarding_scan_found),
                supporting = null,
            ) {
                Text(
                    MoneyFormat.groupThousands(progress.found.toString()),
                    style = FoldFinanceTheme.money.inlineStrong,
                    color = palette.text,
                )
            }
            ListGroupDivider()
            IngestRow(
                icon = Icons.Outlined.ArrowDownward,
                tint = palette.mint,
                title = stringResource(R.string.onboarding_scan_income),
                supporting = pluralStringResource(R.plurals.onboarding_operations, progress.incomeCount, progress.incomeCount),
            ) {
                MoneyText(progress.incomeRubMinor, RUB, direction = Direction.INCOME, style = MoneyStyle.InlineStrong)
            }
            ListGroupDivider()
            IngestRow(
                icon = Icons.Outlined.ArrowUpward,
                tint = palette.textMuted,
                title = stringResource(R.string.onboarding_scan_expense),
                supporting = pluralStringResource(R.plurals.onboarding_operations, progress.expenseCount, progress.expenseCount),
            ) {
                MoneyText(progress.expenseRubMinor, RUB, direction = Direction.EXPENSE, style = MoneyStyle.InlineStrong)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_scan_rub_note), style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
    }
}

private const val RUB = "RUB"

private fun ScanState.progressOrEmpty(): ScanProgress = when (this) {
    is ScanState.Running -> progress
    is ScanState.Finished -> progress
    ScanState.Idle, ScanState.Failed -> ScanProgress()
}
