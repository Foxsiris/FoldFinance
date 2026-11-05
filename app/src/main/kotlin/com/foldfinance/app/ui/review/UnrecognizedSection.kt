package com.foldfinance.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.formatDate
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.core.model.RawMessage
import java.time.Instant
import java.time.ZoneId

/**
 * Entry to «Не распознано» under the Review header: shown only when there are such messages, so
 * the screen looks exactly as before when everything was recognised.
 */
@Composable
internal fun UnrecognizedEntry(state: ReviewUiState, actions: ReviewActions, modifier: Modifier = Modifier) {
    val count = state.unrecognized.size
    if (count == 0) return
    TextButton(
        onClick = actions::openUnrecognized,
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = modifier.heightIn(min = 48.dp).testTag("review.unrecognized.entry"),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = FoldFinanceTheme.palette.saffron)
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.review_unrecognized_count, count, count),
            color = FoldFinanceTheme.palette.saffron,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * «Не распознано»: the original bank texts, each with «Добавить вручную» (opens the add form with
 * the text as the note) and «Скрыть».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnrecognizedSheet(state: ReviewUiState, actions: ReviewActions, now: Instant, zone: ZoneId) {
    if (!state.unrecognizedOpen) return
    val palette = FoldFinanceTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = now.atZone(zone).toLocalDate()
    ModalBottomSheet(
        onDismissRequest = actions::closeUnrecognized,
        sheetState = sheetState,
        shape = FoldShapes.sheet,
        containerColor = palette.surface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().testTag("review.unrecognized.sheet"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        stringResource(R.string.review_unrecognized_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.review_unrecognized_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                }
            }
            items(state.unrecognized, key = { it.id }) { message ->
                UnrecognizedItem(
                    message = message,
                    meta = stringResource(
                        R.string.review_unrecognized_meta,
                        message.sender,
                        formatDate(message.receivedAt.atZone(zone).toLocalDate(), today),
                        formatTime(message.receivedAt, zone),
                    ),
                    onAdd = { actions.addUnrecognizedManually(message.id) },
                    onHide = { actions.hideUnrecognized(message.id) },
                )
            }
        }
    }
}

@Composable
private fun UnrecognizedItem(message: RawMessage, meta: String, onAdd: () -> Unit, onHide: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    ListGroup(Modifier.fillMaxWidth(), color = palette.card) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = palette.textMuted, modifier = Modifier.padding(end = 8.dp))
            Spacer(Modifier.height(4.dp))
            Text(message.text, style = com.foldfinance.app.ui.theme.FoldTextStyles.mono, color = palette.textSoft, modifier = Modifier.padding(end = 8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onHide, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.review_unrecognized_hide), color = palette.textMuted, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.review_unrecognized_add), color = palette.saffron, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
