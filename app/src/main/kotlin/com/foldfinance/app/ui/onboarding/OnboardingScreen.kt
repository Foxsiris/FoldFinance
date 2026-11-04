package com.foldfinance.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ingest.ScanState
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Stateless onboarding screen — one step of [OnboardingUiState].
 *
 * Layout (docs/design.md):
 * - outer screen (Compact): one column; explanation on top, status and buttons in the bottom half
 *   (thumb zone);
 * - inner screen (Book): [TwoPane] split on the hinge — explanation on the left, action on the right;
 * - tabletop: explanation, instructions and progress on top (to look at), buttons — and the
 *   source checklist — at the bottom (to touch).
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    val content = stepContent(state, actions)
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        StepLayout(
            layout = layout,
            counter = stringResource(R.string.onboarding_step_counter, state.stepNumber, state.stepCount),
            content = content,
        )
    }
}

/**
 * What a step shows.
 *
 * @property glance status, instructions or progress — things to read. On tabletop it goes to the
 *   top half with the explanation.
 * @property actionBody interactive content (the source checklist, counters next to the button).
 *   On tabletop it goes to the bottom half above the buttons.
 * @property bodyScrolls `false` when [actionBody] brings its own lazy list; it then fills the free
 *   height instead of scrolling.
 */
internal class StepContent(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val note: String? = null,
    val bodyScrolls: Boolean = true,
    /** Drop the icon tile on the outer screen when the body needs the height (scan counters). */
    val compactOnOuter: Boolean = false,
    val glance: (@Composable ColumnScope.() -> Unit)? = null,
    val actionBody: (@Composable ColumnScope.() -> Unit)? = null,
    val buttons: @Composable ColumnScope.() -> Unit,
)

@Composable
private fun StepLayout(layout: AppLayout, counter: String, content: StepContent) {
    val buttons: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) { content.buttons(this) }
    }
    when (layout) {
        is AppLayout.Compact -> Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (content.bodyScrolls) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Explanation(counter, content, compact = content.compactOnOuter)
                    Body(content)
                }
            } else {
                Explanation(counter, content, compact = true)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f)) { content.actionBody?.invoke(this) }
            }
            Spacer(Modifier.height(12.dp))
            buttons(Modifier)
        }

        // Tabletop: to read on top, to touch at the bottom.
        is AppLayout.Tabletop -> TwoPane(
            modifier = Modifier.fillMaxSize(),
            layout = layout,
            first = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                ) {
                    Explanation(counter, content, compact = content.glance != null || !content.bodyScrolls)
                    content.glance?.let { glance ->
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.widthIn(max = 640.dp)) { glance() }
                    }
                }
            },
            second = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val action = content.actionBody
                    if (action != null) {
                        val body = Modifier
                            .weight(1f)
                            .widthIn(max = 640.dp)
                        Column(if (content.bodyScrolls) body.verticalScroll(rememberScrollState()) else body) { action() }
                        Spacer(Modifier.height(12.dp))
                    }
                    buttons(Modifier.widthIn(max = 640.dp))
                }
            },
        )

        is AppLayout.Book -> TwoPane(
            modifier = Modifier.fillMaxSize(),
            layout = layout,
            first = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                ) { Explanation(counter, content) }
            },
            second = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                ) {
                    val body = Modifier
                        .weight(1f)
                        .widthIn(max = 560.dp)
                    if (content.bodyScrolls) {
                        Column(body.verticalScroll(rememberScrollState())) { Body(content, topSpacing = false) }
                    } else {
                        Column(body) { content.actionBody?.invoke(this) }
                    }
                    Spacer(Modifier.height(12.dp))
                    buttons(Modifier.widthIn(max = 560.dp))
                }
            },
        )
    }
}

/** [StepContent.glance] followed by [StepContent.actionBody]. */
@Composable
private fun ColumnScope.Body(content: StepContent, topSpacing: Boolean = true) {
    val glance = content.glance
    val action = content.actionBody
    if (glance != null) {
        if (topSpacing) Spacer(Modifier.height(24.dp))
        glance()
    }
    if (action != null) {
        Spacer(Modifier.height(if (glance != null || topSpacing) 16.dp else 0.dp))
        action()
    }
}

@Composable
private fun Explanation(counter: String, content: StepContent, compact: Boolean = false) {
    val palette = FoldFinanceTheme.palette
    Column {
        Text(counter, style = MaterialTheme.typography.labelLarge, color = palette.textMuted)
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        EmptyState(title = content.title, body = content.body, icon = if (compact) null else content.icon)
        val note = content.note
        if (note != null) {
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            Text(
                text = note,
                modifier = Modifier.widthIn(max = 420.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }
    }
}

@Composable
private fun stepContent(state: OnboardingUiState, actions: OnboardingActions): StepContent {
    val perms = state.permissions
    return when (state.step) {
        OnboardingStep.SmsIntro -> StepContent(
            icon = Icons.Outlined.Sms,
            title = stringResource(R.string.onboarding_sms_title),
            body = stringResource(R.string.onboarding_sms_body),
            note = stringResource(R.string.onboarding_sms_privacy),
            glance = if (perms.sms) { { StatusLine(true, stringResource(R.string.onboarding_sms_granted)) } } else null,
            buttons = {
                if (perms.sms) {
                    PrimaryAction(stringResource(R.string.onboarding_next), actions::next)
                } else {
                    PrimaryAction(stringResource(R.string.onboarding_sms_action), actions::requestSms)
                    SkipAction(actions::skip)
                }
            },
        )

        OnboardingStep.SmsRestricted -> StepContent(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.onboarding_restricted_title),
            body = stringResource(R.string.onboarding_restricted_body),
            glance = {
                InstructionList(
                    listOf(
                        stringResource(R.string.onboarding_restricted_step1),
                        stringResource(R.string.onboarding_restricted_step2),
                        stringResource(R.string.onboarding_restricted_step3),
                        stringResource(R.string.onboarding_restricted_step4),
                    ),
                )
            },
            buttons = {
                PrimaryAction(stringResource(R.string.onboarding_open_app_details), actions::openAppDetails)
                SecondaryAction(stringResource(R.string.onboarding_request_again), actions::requestSms)
                SkipAction(actions::skip)
            },
        )

        OnboardingStep.NotificationAccess -> StepContent(
            icon = Icons.Outlined.NotificationsActive,
            title = stringResource(R.string.onboarding_push_title),
            body = stringResource(R.string.onboarding_push_body),
            note = stringResource(R.string.onboarding_push_privacy),
            glance = {
                if (perms.notificationAccess) {
                    StatusLine(true, stringResource(R.string.onboarding_push_granted))
                } else {
                    InstructionList(listOf(stringResource(R.string.onboarding_push_restricted)))
                }
            },
            buttons = {
                if (perms.notificationAccess) {
                    PrimaryAction(stringResource(R.string.onboarding_next), actions::next)
                } else {
                    PrimaryAction(stringResource(R.string.onboarding_push_action), actions::openNotificationAccess)
                    SecondaryAction(stringResource(R.string.onboarding_open_app_details), actions::openAppDetails)
                    SkipAction(actions::skip)
                }
            },
        )

        OnboardingStep.NotificationSources -> StepContent(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.onboarding_sources_title),
            body = stringResource(R.string.onboarding_sources_body),
            bodyScrolls = false,
            actionBody = {
                NotificationSourcesList(
                    sources = state.sources,
                    onToggle = actions::toggleSource,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            buttons = {
                PrimaryAction(
                    stringResource(R.string.onboarding_sources_save),
                    actions::saveSources,
                    enabled = state.sources.apps != null,
                )
                SkipAction(actions::skip)
            },
        )

        OnboardingStep.PostNotifications -> StepContent(
            icon = Icons.Outlined.Sync,
            title = stringResource(R.string.onboarding_progress_title),
            body = stringResource(R.string.onboarding_progress_body),
            glance = if (perms.postNotifications) { { StatusLine(true, stringResource(R.string.onboarding_progress_granted)) } } else null,
            buttons = {
                if (perms.postNotifications) {
                    PrimaryAction(stringResource(R.string.onboarding_next), actions::next)
                } else {
                    PrimaryAction(stringResource(R.string.onboarding_progress_action), actions::requestPostNotifications)
                    SkipAction(actions::skip)
                }
            },
        )

        OnboardingStep.Battery -> StepContent(
            icon = Icons.Outlined.BatteryChargingFull,
            title = stringResource(R.string.onboarding_battery_title),
            body = stringResource(R.string.onboarding_battery_body),
            glance = {
                StatusLine(
                    ok = perms.batteryUnrestricted,
                    text = stringResource(
                        if (perms.batteryUnrestricted) R.string.onboarding_battery_ok else R.string.onboarding_battery_limited,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                InstructionList(
                    listOf(
                        stringResource(R.string.onboarding_battery_step1),
                        stringResource(R.string.onboarding_battery_step2),
                    ),
                )
            },
            buttons = {
                if (perms.batteryUnrestricted) {
                    PrimaryAction(stringResource(R.string.onboarding_next), actions::next)
                } else {
                    PrimaryAction(stringResource(R.string.onboarding_battery_action), actions::openBatterySettings)
                    SkipAction(actions::skip)
                }
            },
        )

        OnboardingStep.Scan -> if (!perms.sms) {
            StepContent(
                icon = Icons.Outlined.Sms,
                title = stringResource(R.string.onboarding_scan_no_sms_title),
                body = stringResource(R.string.onboarding_scan_no_sms_body),
                buttons = { PrimaryAction(stringResource(R.string.onboarding_done), actions::finish) },
            )
        } else {
            StepContent(
                icon = Icons.Outlined.Sync,
                title = stringResource(R.string.onboarding_scan_title),
                body = stringResource(R.string.onboarding_scan_body),
                compactOnOuter = true,
                glance = { ScanHeadline(state.scan, Modifier.fillMaxWidth()) },
                actionBody = { ScanCounters(state.scan, Modifier.fillMaxWidth()) },
                buttons = {
                    when (state.scan) {
                        is ScanState.Running ->
                            PrimaryAction(stringResource(R.string.onboarding_scan_background), actions::finish)
                        is ScanState.Finished, ScanState.Failed ->
                            PrimaryAction(stringResource(R.string.onboarding_done), actions::finish)
                        ScanState.Idle -> {
                            PrimaryAction(stringResource(R.string.onboarding_scan_start), actions::startScan)
                            SkipAction(actions::finish)
                        }
                    }
                },
            )
        }
    }
}
