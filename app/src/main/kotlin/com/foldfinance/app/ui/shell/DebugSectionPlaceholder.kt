package com.foldfinance.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Content of a section that is not ready yet. Reachable only in debug builds: release hides
 * such sections from navigation ([Sections.visible]).
 *
 * @param extra optional debug tools under the note.
 */
@Composable
fun DebugSectionPlaceholder(
    section: Section,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(modifier.padding(24.dp)) {
        Text(
            text = stringResource(section.labelRes),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = FoldFinanceTheme.palette.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.section_placeholder_debug),
            style = MaterialTheme.typography.bodyLarge,
            color = FoldFinanceTheme.palette.textMuted,
        )
        if (extra != null) {
            Spacer(Modifier.height(24.dp))
            extra()
        }
    }
}
