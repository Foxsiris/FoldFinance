package com.foldfinance.app.ingest.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.SectionHeader
import com.foldfinance.app.ui.onboarding.IngestRow
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Debug tools of the ingest layer for the settings screen: «Отправить тестовое банковское
 * уведомление» and «Вставить тестовую SMS». Renders nothing in release builds.
 */
@Composable
fun IngestDebugTools(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    val viewModel: IngestDebugViewModel = hiltViewModel()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val palette = FoldFinanceTheme.palette
    Column(modifier) {
        SectionHeader(title = stringResource(R.string.ingest_debug_title))
        Spacer(Modifier.height(8.dp))
        ListGroup(Modifier.fillMaxWidth()) {
            IngestRow(
                icon = Icons.Outlined.NotificationAdd,
                title = stringResource(R.string.ingest_debug_push_title),
                supporting = stringResource(R.string.ingest_debug_push_body),
                onClick = viewModel::sendTestNotification,
            )
            ListGroupDivider()
            IngestRow(
                icon = Icons.Outlined.Sms,
                title = stringResource(R.string.ingest_debug_sms_title),
                supporting = stringResource(R.string.ingest_debug_sms_body),
                onClick = viewModel::insertTestSms,
            )
        }
        val current = result
        if (current != null) {
            Text(
                text = stringResource(current.messageRes()),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (current == DebugResult.Failed) palette.alert else palette.textMuted,
            )
        }
    }
}

private fun DebugResult.messageRes(): Int = when (this) {
    DebugResult.NotificationPosted -> R.string.ingest_debug_result_posted
    DebugResult.NotificationNoPermission -> R.string.ingest_debug_result_no_permission
    DebugResult.SmsRecorded -> R.string.ingest_debug_result_recorded
    DebugResult.SmsAlreadyProcessed -> R.string.ingest_debug_result_already
    DebugResult.SmsNotFinancial -> R.string.ingest_debug_result_not_financial
    DebugResult.SmsUnrecognized -> R.string.ingest_debug_result_unrecognized
    DebugResult.Failed -> R.string.ingest_debug_result_failed
}
